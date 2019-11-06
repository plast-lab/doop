package org.clyze.doop.common.scanner;

import java.io.*;
import java.util.*;
import org.clyze.doop.common.Database;
import org.clyze.doop.common.PredicateFile;

import static org.clyze.doop.common.PredicateFile.*;

/**
 * A binary analysis that is available to the native scanner.
 */
abstract class BinaryAnalysis {

    // Dummy value for "offset" column in facts.
    private static final String UNKNOWN_OFFSET = "-1";
    // Dummy value for "function" column in facts.
    static final String UNKNOWN_FUNCTION = "-";
    // Dummy address.
    public static final long UNKNOWN_ADDRESS = -1;

    // The database object to use for writing facts.
    private final Database db;
    // The native code library.
    final String lib;
    // The entry points table.
    final SortedMap<Long, String> entryPoints = new TreeMap<>();
    // String precision option.
    private final boolean onlyPreciseNativeStrings;

    // The native code architecture.
    protected Arch arch;

    BinaryAnalysis(Database db, String lib, boolean onlyPreciseNativeStrings) {
        this.db = db;
        this.lib = lib;
        this.onlyPreciseNativeStrings = onlyPreciseNativeStrings;

        // Auto-detect architecture.
        try {
            this.arch = autodetectArch();
        } catch (IOException ex) {
            this.arch = Arch.DEFAULT_ARCH;
        }
    }

    /**
     * Return a set of the strings found in a binary.
     *
     * @return a map of address-to-string entries
     */
    public SortedMap<Long, String> findStrings() throws IOException {
        Section rodata = getSection(".rodata");
        return rodata == null ? null : rodata.strings();
    }

    /**
     * Find string cross-references.
     *
     * @param binStrings   the string table (offset-string entries)
     * @return             a mapping from strings to references in code
     */
    abstract Map<String, Set<XRef>> findXRefs(Map<Long, String> binStrings) throws IOException;

    /**
     * Initialize the entry points table of the library.
     */
    abstract void initEntryPoints() throws IOException;

    /**
     * Autodetect the target hardware architecture.
     */
    abstract protected Arch autodetectArch() throws IOException;

    /**
     * Returns a list of pointer values that may point to global data.
     */
    public Set<Long> getGlobalDataPointers() throws IOException {
        Section data = getSection(".data");
        return data == null ? null : data.analyzeWords();
    }

    /**
     * Reads a section by name.
     */
    abstract Section getSection(String sectionName) throws IOException;

    /**
     * Write the facts computed by the analysis.
     */
    void writeFacts(Map<String, Set<XRef>> xrefs,
                    Map<String, List<SymbolInfo>> nameSymbols,
                    Map<String, List<SymbolInfo>> methodTypeSymbols) {

        // Write out symbol tables.
        Set<Long> dataPointers;
        try {
            dataPointers = getGlobalDataPointers();
        } catch (IOException ex) {
            System.err.println("Could not find global data pointers: " + ex.getMessage());
            dataPointers = new HashSet<>();
        }
        writeSymbolTable(db, NATIVE_NAME_CANDIDATE, nameSymbols, xrefs, dataPointers);
        writeSymbolTable(db, NATIVE_METHODTYPE_CANDIDATE, methodTypeSymbols, xrefs, dataPointers);

        // Write xrefs.
        xrefs.forEach((s, refs) -> refs.forEach(xr -> writeXRef(s, xr)));

        // Write entry points.
        entryPoints.forEach((addr, name) ->
                            db.add(NATIVE_LIB_ENTRY_POINT, lib, name, String.valueOf(addr)));
    }

    /**
     * Write the full symbol table. This method can also be extended
     * to merge information per symbol (for example, if different
     * entries contain complementary information).
     *
     * @param db         the database object to use
     * @param factsFile  the facts file to use for writing
     * @param symbols    the symbols table
     * @param xrefs      the string xrefs table
     * @param words      a set of machine words that might contain string pointers
     */
    private void writeSymbolTable(Database db, PredicateFile factsFile,
                                  Map<String, List<SymbolInfo> > symbols,
                                  Map<String, Set<XRef>> xrefs,
                                  Collection<Long> words) {
        for (Map.Entry<String, List<SymbolInfo>> entry : symbols.entrySet()) {
            String symbol = entry.getKey();
            for (SymbolInfo si : entry.getValue()) {
                String offset = si.offset == null ? UNKNOWN_OFFSET : Long.toString(si.offset);
                // If used in global data, set dummy function name for string.
                String func = si.function;
                if (func.equals(UNKNOWN_FUNCTION) && words != null && words.contains(si.offset))
                    func = "<<GLOBAL_DATA_SECTION>>";
                // Skip strings used in unknown locations if appropriate option is set.
                boolean skipString = onlyPreciseNativeStrings && func.equals(UNKNOWN_FUNCTION) && (xrefs.get(symbol) == null);
                if (!skipString)
                    db.add(factsFile, si.lib, func, symbol, offset);
            }
        }
    }

    private void writeXRef(String s, XRef xr) {
        db.add(NATIVE_XREF, s, lib, xr.function, String.valueOf(xr.codeAddr));
    }

    // Auxiliary hex converter.
    static long hexToLong(String s) {
        if (s.startsWith("0x"))
            s = s.substring(2);
        return Long.parseLong(s.trim(), 16);
    }

    // Auxiliary hex converter.
    static int hexToInt(String s) {
        if (s.startsWith("0x"))
            s = s.substring(2);
        return Integer.parseInt(s.trim(), 16);
    }
}
