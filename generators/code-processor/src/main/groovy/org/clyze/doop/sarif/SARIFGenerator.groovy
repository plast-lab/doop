package org.clyze.doop.sarif

import groovy.transform.CompileStatic
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import org.clyze.persistent.model.Element
import org.clyze.persistent.model.Position
import org.clyze.persistent.model.SymbolWithId
import org.clyze.persistent.model.Usage
import org.clyze.persistent.model.jvm.JvmClass
import org.clyze.persistent.model.jvm.JvmField
import org.clyze.persistent.model.jvm.JvmHeapAllocation
import org.clyze.persistent.model.jvm.JvmMethod
import org.clyze.persistent.model.jvm.JvmMethodInvocation
import org.clyze.persistent.model.jvm.JvmVariable
import org.clyze.sarif.model.ArtifactLocation
import org.clyze.sarif.model.Location
import org.clyze.sarif.model.Message
import org.clyze.sarif.model.Result
import org.clyze.sarif.model.Rule
import org.clyze.sarif.SARIFWriter

/**
 * This class writes analysis results in the SARIF format.
 * This is an abstract class: concrete subclasses must
 * call processElement() for every code element identified
 * and finally call generateSARIF() to generate the SARIF output.
 */
@CompileStatic
abstract class SARIFGenerator {
    /** The name of the Doop relation that informs the processor. */
    private static final String SARIF_DESC = 'SARIF_InterestingRelation.csv'
    /** The name of the output file. */
    private static final String SARIF_OUT = 'doop.sarif'
    /** The prefix to use for placeholders in messages. */
    private static final String PLACEHOLDER_PRE = '@@'
    /** If true, the processor only parses the input (used for debugging or
     *  by clients using this code as a library). */
    protected final boolean parseOnly
    /** The output directory. */
    protected final File out
    /** A Doop analysis database. */
    protected final File db
    /** True when the processor is called from the main() method. */
    protected final boolean standalone
    /** The version of the generator. */
    protected final String version
    /** Dictionary of Doop id connections to interesting relations. Used to quickly look up
     *  Doop ids for parsed elements. */
    protected final Map<String, Set<RMetadata>> doopIds = new HashMap<>()
    /** Interesting relations stored as relation-name -> doop-id -> line. Used in message reporting. */
    private final Map<String, Map<String, List<String[]>>> relationLines = new HashMap<>()
    /** List of all metadata, to be used to generate the rules list for SARIF. */
    private final List<RMetadata> allMetadata = new LinkedList<>()
    /** The JVM metadata symbols supported. */
    private static final List<Class> JVM_SYMBOLS = Arrays.asList(JvmClass.class, JvmField.class, JvmMethod.class,
            JvmMethodInvocation.class, JvmHeapAllocation.class, JvmVariable.class, Usage.class)

    protected SARIFGenerator(File db, File out, String version, boolean standalone) {
        this.db = db
        this.out = out
        this.version = version
        this.standalone = standalone
        if (db == null || out == null) {
            this.parseOnly = true
            if (standalone)
                println "WARNING: Running parsing check only, see usage help for more options."
        } else {
            this.parseOnly = !readSARIFRelations()
            boolean mkdir = out.mkdirs()
            if (standalone && !mkdir)
                println "WARNING: Directory ${out} already exists."
        }
    }

    /**
     * The main method that generates SARIF output.
     */
    abstract void process()

    /**
     * Reads the analysis output database to gather relation data and metadata.
     * @return   true if interesting relations were found
     */
    private boolean readSARIFRelations() {
        int ruleIndex = 0
        File sarifDesc = new File(db, SARIF_DESC)
        if (!sarifDesc.exists())
            return false
        for (String line : sarifDesc.readLines()) {
            String[] parts = line.tokenize('\t')
            if (parts.length != 6)
                throw new RuntimeException("ERROR: Bad relation arity in ${SARIF_DESC}")
            RMetadata rm = new RMetadata(name: parts[0], doopIdPosition: parts[1] as int, contentType: parts[2], resultMessage: parts[3], ruleDescription: parts[4], ruleIndex: ruleIndex++, level: parts[5])
            allMetadata.add rm
            String relationName = parts[0]
            println "Reading relation: ${relationName}"
            File rel = new File(db, "${relationName}.csv")
            if (!rel.exists()) {
                println "ERROR: Relation does not exist and will not be included in SARIF output: ${rel.canonicalPath}"
                continue
            }
            Map<String, List<String[]>> relLines = new HashMap<>()
            for (String relLine : rel.readLines()) {
                String[] relParts = relLine.tokenize('\t')
                try {
                    String doopId = relParts[rm.doopIdPosition]
                    relLines.computeIfAbsent(doopId, { new ArrayList<String[]>()}).add(relParts)
                    Set<RMetadata> relationMetadata = doopIds.get(doopId) ?: new HashSet<RMetadata>()
                    relationMetadata.add(rm)
                    doopIds.put(doopId, relationMetadata)
                } catch (Throwable t) {
                    println "ERROR: Processing line fails: ${relLine} -> (parts: ${relParts})"
                    t.printStackTrace()
                }
            }
            relationLines.put(relationName, relLines)
        }
        return !(allMetadata.empty)
    }

    /**
     * Generates the SARIF file.
     * @param results   the list of analysis results
     */
    void generateSARIF(List<Result> results) {
        if (parseOnly) {
            println "Parse only mode, no SARIF output will be generated."
            return
        }
        File sarifOut = new File(out, SARIF_OUT)
        int counter = 0
        List<Rule> rules = allMetadata.collect {new Rule("rule-${counter++}", "rule-${it.name}", it.ruleDescription, it.ruleDescription, it.level)} as List<Rule>
        SARIFWriter.write('doop', 'Doop ' + version, version, sarifOut, rules, results)
        println "SARIF results written to file: ${sarifOut.canonicalPath}"
    }

    private List<Result> resultsForSymbol(String doopId, SymbolWithId sym, RMetadata metadata) {
        ArtifactLocation artLoc = new ArtifactLocation(sym.sourceFileName)
        Position pos = sym.position
        Location loc = new Location(artLoc, pos.startLine, pos.startColumn, pos.endLine, pos.endColumn)
        return formatMessages(doopId, metadata).collect { String msg ->
            new Result("rule-${metadata.ruleIndex}", metadata.ruleIndex,
                       new Message(msg), [loc] as List<Location>)
        } as List<Result>
    }

    private List<String> formatMessages(String doopId, RMetadata metadata) {
        String msgTemplate = metadata.resultMessage
        if (!msgTemplate.contains(PLACEHOLDER_PRE))
            return Collections.singletonList(msgTemplate)

        List<String> messages = new ArrayList<>();
        for (String[] lineParts : relationLines[metadata.name][doopId]) {
            String msg = new String(msgTemplate)
            for (int i = 0; i < lineParts.length; i++) {
                String placeholder = PLACEHOLDER_PRE + i
                if (msg.contains(placeholder))
                    msg = msg.replace(placeholder, lineParts[i])
            }
            messages.add(msg)
        }
        return messages
    }

    /**
     * Processes one code element to find relevant analysis results for this symbol.
     * @param results          a list to use for adding analysis results
     * @param e                the code element to process
     * @param elements         a mutable counter for reporting the total number of elements processed
     */
    void processElement(List<Result> results, Element e, AtomicInteger elements) {
        elements.incrementAndGet()
        if (parseOnly)
            return
        if (e instanceof SymbolWithId) {
            SymbolWithId sym = e as SymbolWithId
            String symbolId = sym.symbolId
            Set<RMetadata> metadataTable = doopIds.get(symbolId)
            if (metadataTable != null) {
                for (RMetadata metadata : metadataTable) {
                    for (Class<?> c : JVM_SYMBOLS) {
                        if (metadata.contentType == c.simpleName) {
                            if (c.isInstance(sym))
                                results.addAll resultsForSymbol(symbolId, sym, metadata)
                            else if (!(sym instanceof Usage))
                                println "ERROR: Wrong content type for element ${metadata.contentType}, class = ${c}"
                            break
                        }
                    }
                }
            }
        } else
            println "Element not a symbol with a Doop ID: ${e}"
    }
}
