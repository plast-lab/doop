package org.clyze.doop.core

import groovy.transform.CompileStatic
import groovy.util.logging.Log4j
import java.nio.file.Files
import java.nio.file.Paths
import java.util.stream.Stream
import org.apache.commons.io.FileUtils
import org.clyze.doop.common.CHA
import org.clyze.doop.common.Database
import org.clyze.doop.common.FieldInfo
import org.clyze.doop.common.PredicateFile
import org.clyze.utils.Helper
import static org.clyze.doop.core.FactGenerator0.PredicateFile0.*

// This fact generator handles facts that do not need front-end/IR information.
@Log4j
@CompileStatic
class FactGenerator0 {

    private static final int KEEP_SPEC_COLUMNS = 5

    private File factsDir

    private enum PredicateFile0 {
        DACAPO("Dacapo"),
        KEEP_CLASS("KeepClass"),
        KEEP_METHOD("KeepMethod"),
        KEEP_CLASS_MEMBERS("KeepClassMembers"),
        KEEP_CLASSES_WITH_MEMBERS("KeepClassesWithMembers"),
        MAIN_CLASS("MainClass"),
        ROOT("RootCodeElement"),
        SENSITIVE_LAYOUT_CONTROL("SensitiveLayoutControl"),
        TAINTSPEC("TaintSpec"),
        TAMIFLEX("Tamiflex");

        private final String name

        PredicateFile0(String name) { this.name = name }
    }

    // A map from rule-hash to (type, number-of-matches). Used to detect bad 'keep' input.
    private Map<String, Map<String, Integer> > ruleCounts = new HashMap<String, Map<String, Integer> >()

    FactGenerator0(File factsDir) {
        this.factsDir = factsDir
    }

    private File factsFile(String s) { new File(factsDir, s + ".facts") }

    void writeMainClassFacts(def mainClass) {
        if (mainClass) {
            factsFile(MAIN_CLASS.name).withWriterAppend { w ->
                mainClass.each { w.writeLine(it as String) }
            }
        }
    }

    void writeDacapoFacts(String benchmark, String benchmarkCap) {
        factsFile(DACAPO.name).withWriter { w ->
            w << "dacapo.${benchmark}.${benchmarkCap}Harness" + "\t" + "<dacapo.parser.Config: void setClass(java.lang.String)>"
        }
    }

    void writeDacapoBachFacts(String benchmarkCap) {
        factsFile(DACAPO.name).withWriter { w ->
            w << "org.dacapo.harness.${benchmarkCap}" + "\t" + "<org.dacapo.parser.Config: void setClass(java.lang.String)>"
        }
    }

    void writeTamiflexFacts(File origTamFile) {
        factsFile(TAMIFLEX.name).withWriter { w ->
            origTamFile.eachLine { line ->
                w << line
                    .replaceFirst(/;[^;]*;$/, "")
                    .replaceFirst(/;$/, ";0")
                    .replaceFirst(/(^.*;.*)\.([^.]+;[0-9]+$)/) { full, first, second -> first + ";" + second + "\n" }
                    .replaceAll(";", "\t").replaceFirst(/\./, "\t")
            }
        }
    }

    private void fillCHAFromSootFacts(CHA cha) {
		String supFile = "${factsDir}/${PredicateFile.DIRECT_SUPER_CLASS.toString()}.facts"
		log.info "Importing non-dex class type hierarchy from ${supFile}"
		Helper.forEachLineIn(supFile, { String line ->
			def parts = line.tokenize('\t')
			cha.registerSuperClass(parts[0], parts[1])
		})

		String fieldFile = "${factsDir}/${PredicateFile.FIELD_SIGNATURE.toString()}.facts"
		log.info "Importing non-dex fields from ${fieldFile}"
		Map<String, List<FieldInfo> > fields = [:].withDefault { [] }
		Helper.forEachLineIn(fieldFile, { String line ->
			def parts = line.tokenize('\t')
			String declType = parts[1]
			String name = parts[2]
			String type = parts[3]
			List<FieldInfo> info = fields.get(declType)
			info.add(new FieldInfo(type, name))
			fields.put(declType, info)
		})
		fields.each { declType, fs -> cha.registerDefinedClassFields(declType, fs) }
    }

    // The extra sensitive controls are given as a String
    // "id1,type1,parentId1,id2,type2,parentId2,...".
    void writeExtraSensitiveControls(String controls) {
        if (controls == "") {
            return
        }

        final String DELIM = ","
        String[] parts = controls.split(DELIM)
        int partsLen = parts.length
        if (partsLen % 3 != 0) {
            log.error("Extra sensitive controls list size is " + partsLen + ", not a multiple of 3: \"" + controls + "\"")
            return
        }
        for (int i = 0; i < partsLen; i += 3) {
            String control = parts[i] + DELIM + parts[i+1] + DELIM + parts[i+2]
            try {
                long controlId = Long.parseLong(parts[i])
                String typeId = parts[i+1].trim()
                long parentId = Long.parseLong(parts[i+2])
                log.info "Adding sensitive layout control: ${control}"
                factsFile(PredicateFile.SENSITIVE_LAYOUT_CONTROL.toString()).withWriterAppend { w ->
                    w << controlId + "\t" + typeId + "\t" + parentId + "\n"
                }
            } catch (Exception ex) {
                log.warn "WARNING: Ignoring control: ${control} (exception: '${ex.message}')"
            }
        }
    }

    /**
     * Write the 'keep' specification for entry points.
     *
     * @param specPath   the specification file path
     */
    void writeKeepSpec(String specPath) {
        if (specPath == null)
            return

        if ((new File(specPath)).exists()) {
            log.info "Reading specification from: ${specPath}"
            Files.lines(Paths.get(specPath)).withCloseable { Stream<String> stream ->
                try {

                    Database db = new Database(factsDir.canonicalPath)
                    stream.forEach ({ String s -> processKeepSpecLine(db, s) } as java.util.function.Consumer<String>)
                    db.flush()
                    db.close()

                    ruleCounts.each { ruleHash, typeCounts ->
                        int counts = (new HashSet<Integer>(typeCounts.values())).size()
                        if (counts > 1) {
                            log.warn "WARNING: Rule ${ruleHash} matches different member counts for different types: ${typeCounts}"
                        }
                    }
                    ruleCounts.clear()
                } catch (IOException ex) {
                    log.error "Error writing entry point: ${ex.message}"
                }
            }
        } else
            log.warn "WARNING: Cannot read keep specification: ${specPath}"
    }

    /**
     * The main processor for keep specification lines.
     *
     * @param db     the database object to use for writing
     * @param line   the text line to process
     */
    private void processKeepSpecLine(Database db, String line) {
        String[] fields = line.split("\t")

        if (fields.length != KEEP_SPEC_COLUMNS) {
            log.warn("WARNING: Malformed line (should be $KEEP_SPEC_COLUMNS columns): $line")
            return
        } else
            log.debug "Processing line: $fields"

        String elementId = fields[3]
        switch (fields[0]) {
            case "ROOT":
                factsFile(ROOT.name).withWriterAppend { it << (elementId + "\n") }
                break
            case "KEEP":
                factsFile(KEEP_METHOD.name).withWriterAppend { it << (elementId + "\n") }
                break
            case "TAINT":
                factsFile(TAINTSPEC.name).withWriterAppend { it << (fields[1] + "\t" + fields[2] + "\t" + elementId + "\n") }
                break
            case "REMOVE":
            case "OBFUSCATE":
                log.warn "WARNING: Ignoring line, not useful for analysis: $line"
                break
            case "KEEP_CLASS":
                factsFile(KEEP_CLASS.name).withWriterAppend { it << (elementId + "\n") }
                break
            case "KEEP_CLASS_MEMBERS":
                factsFile(KEEP_CLASS_MEMBERS.name).withWriterAppend { it << (elementId + "\n") }
                break
            case "KEEP_CLASSES_WITH_MEMBERS":
                factsFile(KEEP_CLASSES_WITH_MEMBERS.name).withWriterAppend { it << (elementId + "\n") }
                break
            default:
                log.warn "WARNING: Unsupported spec line: ${line}"
        }
    }

    /**
     * Initialize all output fact files.
     */
    void touch() {
        PredicateFile0.values().each {
            FileUtils.touch(factsFile(it.name))
        }
    }
}
