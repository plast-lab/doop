package org.clyze.doop.sarif

import groovy.transform.CompileStatic
import java.util.concurrent.atomic.AtomicInteger
import org.clyze.persistent.model.Element
import org.clyze.persistent.model.Position
import org.clyze.persistent.model.SymbolWithId
import org.clyze.persistent.model.Usage
import org.clyze.persistent.model.jvm.JvmClass
import org.clyze.persistent.model.jvm.JvmField
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
 */
@CompileStatic
class SARIFGenerator {
    /** The name of the Doop relation that informs the processor. */
    private static final String SARIF_DESC = 'SARIF_InterestingRelation.csv'
    /** The name of the output file. */
    private static final String SARIF_OUT = 'doop.sarif'
    /** The prefix to use for placeholders in messages. */
    private static final String PLACEHOLDER_PRE = '@@'
    /** If true, the processor only parses the input (used for debugging or
     *  by clients using this code as a library). */
    protected final boolean parseOnly = false
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
    private final Map<String, Map<String, String[]>> relationLines = new HashMap<>()
    /** List of all metadata, to be used to generate the rules list for SARIF. */
    private final List<RMetadata> allMetadata = new LinkedList<>()

    SARIFGenerator(File db, File out, String version, boolean standalone) {
        if (db == null || out == null) {
            this.parseOnly = true
            if (standalone)
                println "WARNING: running parsing check only, see usage help for more options."
        }
        this.db = db
        this.out = out
        this.version = version
        this.standalone = standalone
    }

    /**
     * Checks if analysis metadata exist.
     * @return true when the analysis database (containing the metadata) exists
     */
    boolean metadataExist() {
        boolean metadataExist = false
        if (!parseOnly) {
            metadataExist = readDatabase()
            boolean mkdir = out.mkdirs()
            if (standalone && !mkdir)
                println "WARNING: directory ${out} already exists."
        }
        return metadataExist
    }

    /**
     * Reads the analysis output database to gather relation data and metadata.
     * @return   true if interesting relations were found
     */
    private boolean readDatabase() {
        int ruleIndex = 0
        for (String line : new File(db, SARIF_DESC).readLines()) {
            String[] parts = line.tokenize('\t')
            if (parts.length != 6)
                throw new RuntimeException("ERROR: bad relation arity in ${SARIF_DESC}")
            RMetadata rm = new RMetadata(name: parts[0], doopIdPosition: parts[1] as int, contentType: parts[2], resultMessage: parts[3], ruleDescription: parts[4], ruleIndex: ruleIndex++, level: parts[5])
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

    /**
     * Generates the SARIF file.
     * @param results   the list of analysis results
     */
    void generateSARIF(List<Result> results) {
        File sarifOut = new File(out, SARIF_OUT)
        int counter = 0
        List<Rule> rules = allMetadata.collect {new Rule("rule-${counter++}", "rule-${it.name}", it.ruleDescription, it.ruleDescription, it.level)} as List<Rule>
        SARIFWriter.write('doop', 'Doop ' + version, version, sarifOut, rules, results)
        println "SARIF results written to file: ${sarifOut.canonicalPath}"
    }

    private Result resultForSymbol(String doopId, SymbolWithId sym, RMetadata metadata) {
        ArtifactLocation artLoc = new ArtifactLocation(sym.sourceFileName)
        Position pos = sym.position
        Location loc = new Location(artLoc, pos.startLine, pos.startColumn, pos.endLine, pos.endColumn)
        return new Result("rule-${metadata.ruleIndex}", metadata.ruleIndex,
                new Message(formatMessage(doopId, metadata)), [loc] as List<Location>)
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

    /**
     * Processes one code element to find relevant analysis results for this symbol.
     * @param metadataExist    if false, this method only counts elements, without processing them
     * @param results          a list to used for adding analysis results
     * @param e                the code element to process
     * @param elements         a mutable counter for reporting the total number of elements processed
     */
    void processElement(boolean metadataExist, List<Result> results, Element e, AtomicInteger elements) {
        elements.incrementAndGet()
        if (parseOnly || !metadataExist)
            return
        if (e instanceof SymbolWithId) {
            SymbolWithId sym = e as SymbolWithId
            String symbolId = sym.symbolId
            Set<RMetadata> metadataTable = doopIds.get(symbolId)
            if (metadataTable != null) {
                for (RMetadata metadata : metadataTable) {
                    switch (metadata.contentType) {
                        case 'JvmMethod':
                            if (sym instanceof JvmMethod)
                                results.add resultForSymbol(symbolId, sym, metadata)
                            else
                                println "ERROR: wrong content type for element ${metadata.contentType}"
                            break
                        case 'JvmMethodInvocation':
                            if (sym instanceof JvmMethodInvocation)
                                results.add resultForSymbol(symbolId, sym, metadata)
                            else
                                println "ERROR: wrong content type for element ${metadata.contentType}"
                            break
                        case 'JvmVariable':
                            if (sym instanceof JvmVariable)
                                results.add resultForSymbol(symbolId, sym, metadata)
                            else if (!(sym instanceof Usage))
                                println "ERROR: wrong content type for element ${metadata.contentType}"
                            break
                        case 'JvmField':
                            if (sym instanceof JvmField)
                                results.add resultForSymbol(symbolId, sym, metadata)
                            else if (!(sym instanceof Usage))
                                println "ERROR: wrong content type for element ${metadata.contentType}"
                            break
                        case 'JvmClass':
                            if (sym instanceof JvmClass)
                                results.add resultForSymbol(symbolId, sym, metadata)
                            else if (!(sym instanceof Usage))
                                println "ERROR: wrong content type for element ${metadata.contentType}"
                            break
                    }
                }
            }
        } else
            println "Element not a symbol with a Doop ID: ${e}"
    }
}
