package org.clyze.doop.common.scanner;

import java.io.*;
import java.util.*;
import org.clyze.doop.common.ArtifactScanner;
import org.clyze.doop.common.Database;
import org.clyze.doop.common.Parameters;
import org.clyze.doop.common.PredicateFile;

import static org.clyze.doop.common.PredicateFile.*;

public class NativeScanner {
    // Enable debug messages.
    private final static boolean debug = false;
    private final Database db;
    // Use Radare to find strings.
    private final boolean useRadare;
    // Only output localized strings (i.e. found inside function
    // boundaries). When function boundaries can be determined, this
    // improves precision.
    private final boolean onlyPreciseNativeStrings;
    private final Set<String> methodStrings;

    // Dummy value for "function" column in facts.
    private static final String UNKNOWN_FUNCTION = "-";
    // Dummy value for "offset" column in facts.
    private static final String UNKNOWN_OFFSET = "-1";

    public NativeScanner(Database db, Parameters params, Set<String> methodStrings) {
        this.db = db;
        this.useRadare = params._radare;
        this.onlyPreciseNativeStrings = params._preciseNativeStrings;
        this.methodStrings = methodStrings;

        System.err.println("Initializing native scanner with " + methodStrings.size() + " strings related to methods.");
    }

    public void scanInputs(Iterable<String> inputs) {
        ArtifactScanner.EntryProcessor gProc = (file, entry, entryName) -> {
            boolean isSO = entryName.endsWith(".so");
            boolean isLibsXZS = entryName.endsWith("libs.xzs");
            boolean isLibsZSTD = entryName.endsWith("libs.zstd");
            if (isSO || isLibsXZS || isLibsZSTD) {
                File libTmpFile = ArtifactScanner.extractZipEntryAsFile("native-lib", file, entry, entryName);
                if (isSO)
                    scanLib(libTmpFile, db);
                else if (isLibsXZS)
                    scanXZSLib(libTmpFile, db);
                else if (isLibsZSTD)
                    scanZSTDLib(libTmpFile, db);
            }
        };
        for (String input : inputs) {
            System.out.println("Processing native code in input: " + input);
            try {
                (new ArtifactScanner()).processArchive(input, null, gProc);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    /**
     * Scan a native code library.
     *
     * @param libFile        the native code library
     * @param db             the database object to use for writing
     */
    private void scanLib(File libFile, Database db) {
        try {
            BinaryAnalysis bAnalysis;
            String lib = libFile.getCanonicalPath();
            System.out.println("== Processing library: " + lib + " ==");
            if (useRadare)
                bAnalysis = new RadareAnalysis(db, lib);
            else
                bAnalysis = new BinutilsAnalysis(db, lib);

            bAnalysis.initEntryPoints();

            // Find all strings in the binary.
            System.out.println("Gathering strings from " + lib + "...");
            SortedMap<Long, String> allStrings = bAnalysis.findStrings();
            if (allStrings == null || allStrings.size() == 0) {
                System.err.println("Cannot find strings in " + lib + ", aborting.");
                return;
            }
            System.out.println("Found " + allStrings.size() + " in total.");

            // Filter the strings to work with a more manageable set
            // of strings.
            Map<String, List<SymbolInfo>> nameSymbols = new HashMap<>();
            Map<String, List<SymbolInfo>> methodTypeSymbols = new HashMap<>();
            SortedMap<Long, String> strings = new TreeMap<>();
            for (Map.Entry<Long, String> foundString : allStrings.entrySet()) {
                String s = foundString.getValue();
                // Keep only those that were encountered in the input
                // program as method names or signatures.
                if (!methodStrings.contains(s)) {
                    // System.err.println("Rejecting string: " + s);
                    continue;
                }
                Long addr = foundString.getKey();
                if (isMethodType(s)) {
                    addSymbol(methodTypeSymbols, s, new SymbolInfo(s, lib, UNKNOWN_FUNCTION, addr));
                    strings.put(addr, s);
                } else if (isName(s)) {
                    addSymbol(nameSymbols, s, new SymbolInfo(s, lib, UNKNOWN_FUNCTION, addr));
                    strings.put(addr, s);
                } else
                    // If this code runs, then a method-related string is not a name/type.
                    System.err.println("WARNING: rejecting native string '" + s + "'");
            }
            System.out.println("Filter: " + strings.size() + " out of " + allStrings.size() + " survived.");

            // Find in which function every string is used.
            Map<String, Set<String>> xrefs = bAnalysis.findXRefs(strings);
            System.out.println("Computed " + xrefs.size() + " xrefs.");

            // Write out facts: first write names and method types that
            // belong to known functions, then write everything else (that
            // may be found via radare or parsing the .rodata section).
            // For values that we know their containing function, we set special offsets.
            for (String s : xrefs.keySet()) {
                if (s == null)
                    continue;
                if (isMethodType(s)) {
                    Set<String> funcs = xrefs.get(s);
                    if (funcs != null)
                        for (String function : funcs)
                            addSymbol(methodTypeSymbols, s, new SymbolInfo(s, lib, function, null));
                } else if (isName(s)) {
                    Set<String> funcs = xrefs.get(s);
                    if (funcs != null)
                        for (String function : funcs)
                            addSymbol(nameSymbols, s, new SymbolInfo(s, lib, function, null));
                }
            }

            // Resolve "unknown function" info.
            updateLibSymbolTable(nameSymbols, lib, xrefs);
            updateLibSymbolTable(methodTypeSymbols, lib, xrefs);

            // Write out symbol tables.
            Set<Long> dataPointers = bAnalysis.getGlobalDataPointers();
            int namesCount = nameSymbols.keySet().size();
            System.out.println("Possible method/class names: " + namesCount);
            writeSymbolTable(db, NATIVE_NAME_CANDIDATE, nameSymbols, dataPointers);
            int methodTypesCount = methodTypeSymbols.keySet().size();
            System.out.println("Possible method types found: " + methodTypesCount);
            writeSymbolTable(db, NATIVE_METHODTYPE_CANDIDATE, methodTypeSymbols, dataPointers);

            bAnalysis.writeEntryPoints();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private static boolean isName(String line) {
        char[] chars = line.toCharArray();
        for (int i = 0; i < line.length(); i++) {
            char c = chars[i];
            if ((c != '$') && (c != '/') && (c != '_') &&
                (c != '<') && (c != '>') &&
                !Character.isLetterOrDigit(c)) {
                if (debug)
                    System.err.println("isName(): Rejecting char '" + c + "' : " + line);
                return false;
            }
        }
        return true;
    }

    private static boolean isMethodType(String line) {
        char[] chars = line.toCharArray();
        if (chars.length == 0)
            return false;
        if ((chars[0] != '(') || (!line.contains(")")))
            return false;
        for (int i = 0; i < line.length(); i++) {
            char c = chars[i];
            if ((c != ',') && (c != '/') && (c != '$') && (c != '[') &&
                (c != '(') && (c != ')') && (c != ';') && (c != '_') &&
                (!Character.isLetterOrDigit(c))) {
                if (debug)
                    System.err.println("isMethodType(): Rejecting char '" + c + "' : " + line);
                return false;
            }
        }
        return true;
    }

    static List<String> runCommand(ProcessBuilder builder) throws IOException {
        if (debug)
            System.err.println("Running external command: " + String.join(" ", builder.command()));
        builder.redirectErrorStream(true);
        Process process = builder.start();
        InputStream is = process.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));

        List<String> lines = new LinkedList<>();
        String line;
        while ((line = reader.readLine()) != null)
            lines.add(line);
        return lines;
    }

    // Handle .xzs libraries (found in some .apk inputs).
    private void scanXZSLib(File xzsFile, Database db) {
        String xzsPath = xzsFile.getAbsolutePath();
        System.out.println("Processing xzs-packed native code: " + xzsPath);
        String xzPath = xzsPath.substring(0, xzsPath.length() - 1);
        try {
            // Change .xzs extension to .xz.
            runCommand(new ProcessBuilder("mv", xzsPath, xzPath));
            runCommand(new ProcessBuilder("xz", "--decompress", xzPath));
            File libTmpFile = new File(xzPath.substring(0, xzPath.length() - 3));
            scanLib(libTmpFile, db);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    // Handle .zstd libraries (found in some .apk inputs).
    private void scanZSTDLib(File zstdFile, Database db) {
        String zstdPath = zstdFile.getAbsolutePath();
        System.out.println("Processing zstd-packed native code: " + zstdPath);
        String zstdOutPath = zstdPath.substring(0, zstdPath.length() - 5);
        try {
            runCommand(new ProcessBuilder("zstd", "-d", "-o", zstdOutPath));
            File libTmpFile = new File(zstdOutPath);
            scanLib(libTmpFile, db);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Register a symbol with its info in a table.
     *
     * @param symbols    the symbols table
     * @param symbol     key: the (string) symbol
     * @param si         value: the symbol information
     */
    private static void addSymbol(Map<String, List<SymbolInfo> > symbols,
                                  String symbol, SymbolInfo si) {
        // Ignore @-suffix (such as '@plt' or '@@Base'), since it's not part of the mangled name.
        int atSymbol = symbol.indexOf("@");
        if (atSymbol != -1)
            symbol = symbol.substring(0, atSymbol);
        List<SymbolInfo> infos = symbols.getOrDefault(symbol, new LinkedList<>());
        infos.add(si);
        symbols.put(symbol, infos);
    }

    private static void regUnknown(Collection<String> uSymbols,
                                   String s, SymbolInfo v) {
        if (v.function.equals(UNKNOWN_FUNCTION))
            uSymbols.add(s);
    }

    /**
     * Write the full symbol table. This method can also be extended
     * to merge information per symbol (for example, if different
     * entries contain complementary information).
     *
     * @param db         the database object to use
     * @param factsFile  the facts file to use for writing
     * @param symbols    the symbols table
     * @param words      a set of machine words that might contain string pointers
     */
    private void writeSymbolTable(Database db, PredicateFile factsFile,
                                  Map<String, List<SymbolInfo> > symbols,
                                  Collection<Long> words) {
        for (Map.Entry<String, List<SymbolInfo>> entry : symbols.entrySet()) {
            String symbol = entry.getKey();
            for (SymbolInfo si : entry.getValue()) {
                String offset = si.offset == null ? UNKNOWN_OFFSET : Long.toString(si.offset);
                // If used in global data, set dummy function name for string.
                String func = si.function;
                if (func.equals(UNKNOWN_FUNCTION) && words.contains(si.offset))
                    func = "<<GLOBAL_DATA_SECTION>>";
                // Skip strings belonging to unknown fuctions if option is set.
                boolean skipString = onlyPreciseNativeStrings && func.equals(UNKNOWN_FUNCTION);
                if (!skipString)
                    db.add(factsFile, si.lib, func, symbol, offset);
            }
        }
    }

    private void updateLibSymbolTable(Map<String, List<SymbolInfo>> symbols,
                                      String lib,
                                      Map<String, Set<String>> xrefs) {
        Set<String> unknown = new HashSet<>();
        symbols.forEach((s, v) ->
                        v.forEach(v0 -> regUnknown(unknown, s, v0)));

        final long j = -1;
        for (String uString : unknown) {
            System.out.println("updateLibSymbolTable('" + uString + "')");
            Set<String> uXRefs = xrefs.get(uString);
            if (uXRefs == null)
                continue;
            System.out.println("Found xref information for: " + uString);
            List<SymbolInfo> l = symbols.get(uString);
            for (String xref : uXRefs) {
                l.add(new SymbolInfo(uString, lib, xref, j));
            }
        }
    }
}

class SymbolInfo {
    private final String sym;
    final String lib;
    final String function;
    final Long offset;
    SymbolInfo(String sym, String lib, String function, Long offset) {
        this.sym = sym;
        this.lib = lib;
        this.function = function;
        this.offset = offset;
    }
}
