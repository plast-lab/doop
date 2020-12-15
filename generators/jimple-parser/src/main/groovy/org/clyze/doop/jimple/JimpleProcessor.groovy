package org.clyze.doop.jimple

import groovy.json.JsonBuilder
import groovy.transform.CompileStatic
import org.clyze.persistent.model.Element
import org.clyze.persistent.model.Field
import org.clyze.persistent.model.Method
import org.clyze.persistent.model.MethodInvocation
import org.clyze.persistent.model.Position
import org.clyze.persistent.model.SymbolWithDoopId
import org.clyze.persistent.model.Usage
import org.clyze.persistent.model.Variable

import static groovy.io.FileType.FILES

/**
 * The basic logic of the Jimple processor, which can parse Jimple and
 * generate SARIF metadata that relate Jimple to existing Doop results.
 */
@CompileStatic
class JimpleProcessor {
    /** The name of the Doop relation that informs the processor. */
    private static final String SARIF_DESC = 'SARIF_InterestingRelation.csv'
    /** The name of the output file. */
    private static final String SARIF_OUT = 'doop.sarif'
    /** The prefix to use for placeholders in messages. */
    private static final String PLACEHOLDER_PRE = '@@'

    /** If true, the processor only parses the input (used for debugging). */
    private final boolean parseOnly = false
    /** A Doop analysis database. */
    private final File db
    /** A directory containing Jimple code (in text form). */
    private final String jimplePath
    /** The output directory. */
    private final File out
    /** The version of Doop. */
    private final String version
    /** Dictionary of doop id connections to interesting relations. Used to quickly look up
     *  Doop ids for parsed elements. */
    private final Map<String, Set<RMetadata>> doopIds = new HashMap<>()
    /** Interesting relations stored as relation-name -> doop-id -> line. Used in message reporting. */
    private final Map<String, Map<String, String[]>> relationLines = new HashMap<>()
    /** List of all metadata, to be used to generate the rules list for SARIF. */
    private final List<RMetadata> allMetadata = new LinkedList<>()
    /** True when the processor is called from the main() method. */
    private final boolean standalone

    JimpleProcessor(String jimplePath, File db, File out, String version, boolean standalone) {
        if (db == null || out == null) {
            this.parseOnly = true
            println "WARNING: running parsing check only, see usage help for more options."
        }
        this.jimplePath = jimplePath
        this.db = db
        this.out = out
        this.version = version
        this.standalone = standalone
    }

    /**
     * Main entry point.
     */
    void process() {
        File jimpleDir = new File(jimplePath)

        boolean metadataExist = false
        if (!parseOnly) {
            metadataExist = readDatabase()
            boolean mkdir = out.mkdirs()
            if (standalone && !mkdir)
                println "WARNING: directory ${out} already exists."
        }

        List<Result> results = new LinkedList<>()
        int elements = 0
        if (jimpleDir.exists() && jimpleDir.directory) {
            jimpleDir.eachFileRecurse(FILES) { JimpleListenerImpl.parseJimple(it as String, jimplePath, {
                Element e ->
                    elements++
                    if (parseOnly || !metadataExist)
                        return
                    if (e instanceof SymbolWithDoopId) {
                        SymbolWithDoopId sym = e as SymbolWithDoopId
                        String doopId = sym.doopId
                        Set<RMetadata> metadataTable = doopIds.get(doopId)
                        if (metadataTable != null) {
                            for (RMetadata metadata : metadataTable) {
                                switch (metadata.contentType) {
                                    case 'Method':
                                        if (sym instanceof Method)
                                            results.add resultForSymbol(doopId, sym, metadata)
                                        else
                                            println "ERROR: wrong content type for element ${metadata.contentType}"
                                        break
                                    case 'MethodInvocation':
                                        if (sym instanceof MethodInvocation)
                                            results.add resultForSymbol(doopId, sym, metadata)
                                        else
                                            println "ERROR: wrong content type for element ${metadata.contentType}"
                                        break
                                    case 'Variable':
                                        if (sym instanceof Variable)
                                            results.add resultForSymbol(doopId, sym, metadata)
                                        else if (!(sym instanceof Usage))
                                            println "ERROR: wrong content type for element ${metadata.contentType}"
                                        break
                                    case 'Field':
                                        if (sym instanceof Field)
                                            results.add resultForSymbol(doopId, sym, metadata)
                                        else if (!(sym instanceof Usage))
                                            println "ERROR: wrong content type for element ${metadata.contentType}"
                                        break
                                }
                            }
                        }
                    } else
                        println "Element not a symbol with a Doop ID: ${e}"
            })}

            println "Elements processed: ${elements}"
            if (parseOnly)
                return
            generateSARIF(results)
        } else
            println "ERROR: ${jimplePath} is not a directory."
    }

    /**
     * Reads the analysis output database to gather relation data and metadata.
     * @return   true if interesting relations were found
     */
    private boolean readDatabase() {
        int ruleIndex = 0
        for (String line : new File(db, SARIF_DESC).readLines()) {
            String[] parts = line.tokenize('\t')
            if (parts.length != 5)
                throw new RuntimeException("ERROR: bad relation in ${SARIF_DESC}")
            RMetadata rm = new RMetadata(name: parts[0], doopIdPosition: parts[1] as int, contentType: parts[2], resultMessage: parts[3], ruleDescription: parts[4], ruleIndex: ruleIndex++)
            allMetadata.add rm
            String relationName = parts[0]
            println "Reading relation: ${relationName}"
            File rel = new File(db, "${relationName}.csv")
            if (!rel.exists()) {
                println "ERROR: relation does not exist and will not be included in SARIF output: ${rel.canonicalPath}"
                continue
            }
            Map<String, String[]> relLines = new HashMap<>()
            for (String relLine : rel.readLines()) {
                String[] relParts = relLine.tokenize('\t')
                String doopId = relParts[rm.doopIdPosition]
                relLines.put(doopId, relParts)
                Set<RMetadata> relationMetadata = doopIds.get(doopId) ?: new HashSet<RMetadata>()
                relationMetadata.add(rm)
                doopIds.put(doopId, relationMetadata)
            }
            relationLines.put(relationName, relLines)
        }
        return !(allMetadata.empty)
    }

    private void generateSARIF(List<Result> results) {
        int counter = 0
        List<Rule> rules = allMetadata.collect {new Rule(id: "rule-${counter++}", name: "rule-${it.name}", shortDescription: it.ruleDescription, fullDescription: it.ruleDescription, level: 'error')} as List<Rule>
        Driver driver = new Driver(name: 'doop', fullName: 'Doop ' + version, version: version, semanticVersion: '1.0', rules: rules)
        Run run = new Run(results: results, artifacts: [] as List<Artifact>, tool: new Tool(driver: driver))
        SARIF sarif = new SARIF(runs: [run] as List<Run>)
        File sarifOut = new File(out, SARIF_OUT)
        sarifOut.withWriter { BufferedWriter bw ->
            bw.write(new JsonBuilder(sarif.toMap()).toPrettyString())
        }
        println "SARIF results written to file: ${sarifOut.canonicalPath}"
    }

    private Result resultForSymbol(String doopId, SymbolWithDoopId sym, RMetadata metadata) {
        ArtifactLocation artLoc = new ArtifactLocation(uri: sym.sourceFileName)
        Position pos = sym.position
        Location loc = new Location(artifactLocation: artLoc,
                startLine: pos.startLine, startColumn: pos.startColumn,
                endLine: pos.endLine, endColumn: pos.endColumn)
        return new Result(ruleId: "rule-${metadata.ruleIndex}", ruleIndex: metadata.ruleIndex,
                message: new Message(text: formatMessage(doopId, metadata)),
                locations: [loc] as List<Location>)
    }

    private String formatMessage(String doopId, RMetadata metadata) {
        String msg = metadata.resultMessage
        if (!msg.contains(PLACEHOLDER_PRE))
            return msg
        String[] lineParts = relationLines[metadata.name][doopId]
        for (int i = 0; i < lineParts.length; i++) {
            String placeholder = PLACEHOLDER_PRE + i
            if (msg.contains(placeholder))
                msg = msg.replace(placeholder, lineParts[i])
        }
        return msg
    }
}
