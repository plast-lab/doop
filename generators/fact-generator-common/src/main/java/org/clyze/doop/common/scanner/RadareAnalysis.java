package org.clyze.doop.common.scanner;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Consumer;
import org.clyze.doop.common.Database;

// This class implements the analysis of the native scanner that uses Radare2.
class RadareAnalysis extends BinaryAnalysis {

    private static final boolean debug = false;
    private static final String DOOP_HOME = "DOOP_HOME";
    private static final String doopHome = System.getenv(DOOP_HOME);

    // Radare interface prefixes, see script for details.
    private static final String LOC_MARKER = "STRING_LOC:";
    private static final String STR_MARKER = "STRING:";
    private static final String SEC_MARKER = "SECTION:";
    private static final String EP_MARKER = "ENTRY_POINT:";

    RadareAnalysis(Database db, String lib, boolean onlyPreciseNativeStrings) {
        super(db, lib, onlyPreciseNativeStrings);
    }
    
    private static void runRadare(String... args) throws IOException {
        if (doopHome == null) {
            String msg = "Cannot find Radare script, set environment variable " + DOOP_HOME;
            System.err.println(msg);
            throw new RuntimeException(msg);
        }
        String script = doopHome + "/bin/radare.py";

        List<String> args0 = new LinkedList<>();
        args0.add("python");
        args0.add(script);
        for (String arg : args)
            args0.add(arg);

        ProcessBuilder radareBuilder = new ProcessBuilder(args0.toArray(new String[0]));
        System.out.println("Radare command line: " + radareBuilder.command());

        List<String> output = NativeScanner.runCommand(radareBuilder);
        if (debug)
            output.forEach(System.out::println);
    }

    /**
     * Use Radare (via external tool) to find strings. This method
     * also calls the supertype method to get a base set, which it
     * will then extend.
     *
     * @return a map of address-to-string entries
     */
    @Override
    public SortedMap<Long, String> findStrings() throws IOException {
        System.out.println("Finding strings with Radare2...");
        SortedMap<Long, String> strings = super.findStrings();

        File outFile = File.createTempFile("strings-out", ".txt");

        runRadare("strings", lib, outFile.getCanonicalPath());

        Consumer<ArrayList<String>> proc = (l -> {
                String vAddrStr = l.get(0);
                String s = l.get(1);
                long vAddr = UNKNOWN_ADDRESS;
                try {
                    vAddr = hexToLong(vAddrStr);
                } catch (NumberFormatException ex) {
                    System.err.println("WARNING: error parsing string address: " + vAddrStr);
                }
                strings.put(vAddr, s);
            });
        processMultiColumnFile(outFile, STR_MARKER, 2, proc);
        return strings;
    }

    /**
     * Given a table of strings, returns a map that connects each
     * string with the functions that reference it.
     *
     * @param binStrings  a table of strings
     * @return            a map from strings to (sets of) functions
     */
    @Override
    Map<String, Set<XRef>> findXRefs(Map<Long, String> binStrings) throws IOException {
        System.out.println("Finding string xrefs with Radare2 in: " + lib);

        Map<String, Set<XRef>> xrefs = new HashMap<>();

        File stringsFile = File.createTempFile("strings", ".txt");
        try (FileWriter writer = new FileWriter(stringsFile)) {
            binStrings.forEach((addr, s) -> {
                    // Omit huge strings.
                    if (s.length() > 300)
                        return;
                    // Omit non-Latin strings, since the Python interface
                    // cannot support some UTF-8 codes.
                    boolean nonLatin = false;
                    for (char c : s.toCharArray())
                        if (Character.UnicodeBlock.of(c) != Character.UnicodeBlock.BASIC_LATIN) {
                            nonLatin = true;
                            break;
                        }
                    if (!nonLatin)
                        try {
                            writer.write(addr + " " + s + "\n");
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                });
        }

        File outFile = File.createTempFile("string-xrefs-out", ".txt");

        runRadare("xrefs", lib, stringsFile.getCanonicalPath(), outFile.getCanonicalPath());

        Consumer<ArrayList<String>> proc = (l -> {
                String func = l.get(0);
                String codeAddrStr = l.get(1);
                String s = l.get(2);
                if (func.equals("(nofunc)"))
                    func = UNKNOWN_FUNCTION;
                long codeAddr = UNKNOWN_ADDRESS;
                try {
                    codeAddr = hexToLong(codeAddrStr);
                } catch (NumberFormatException ex) {
                    System.err.println("WARNING: error parsing xref address: " + codeAddrStr);
                }
                xrefs.computeIfAbsent(s, k -> new HashSet<>()).add(new XRef(lib, func, codeAddr));
            });
        processMultiColumnFile(outFile, LOC_MARKER, 3, proc);
        return xrefs;
    }

    @Override
    public Section getSection(String sectionName) throws IOException {
        File outFile = File.createTempFile("sections-out", ".txt");

        runRadare("sections", lib, outFile.getCanonicalPath());

        // Box to use for returning value from section processor.
        Section[] sec = new Section[1];

        Consumer<ArrayList<String>> proc = (l -> {
                String secName = l.get(0);
                if (!secName.equals(sectionName))
                    return;
                String vAddrStr = l.get(1);
                String sizeStr = l.get(2);
                String offsetStr = l.get(3);
                long vAddr = UNKNOWN_ADDRESS;
                int size = 0;
                long offset = 0;
                try {
                    vAddr = hexToLong(vAddrStr);
                    size = Integer.parseInt(sizeStr.trim());
                    offset = hexToLong(offsetStr);
                    sec[0] = new Section(secName, null, lib, size, vAddr, offset);
                } catch (NumberFormatException ex) {
                    System.err.println("WARNING: error parsing section: " + secName + " " + vAddrStr + " " + sizeStr);
                }
            });
        processMultiColumnFile(outFile, SEC_MARKER, 4, proc);
        return sec[0];
    }

    @Override
    public void initEntryPoints() throws IOException {
        File outFile = File.createTempFile("sections-out", ".txt");

        runRadare("epoints", lib, outFile.getCanonicalPath());

        Consumer<ArrayList<String>> proc = (l -> {
                String vAddrStr = l.get(0);
                String name = l.get(1);
                long vAddr = UNKNOWN_ADDRESS;
                try {
                    vAddr = hexToLong(vAddrStr);
                    entryPoints.put(vAddr, name);
                } catch (NumberFormatException ex) {
                    System.err.println("WARNING: error parsing section: " + vAddrStr + " " + name);
                }
            });
        processMultiColumnFile(outFile, EP_MARKER, 2, proc);
    }

    private void processMultiColumnFile(File f, String prefix, int numColumns,
                                        Consumer<ArrayList<String>> proc) throws IOException {
        for (String line : Files.readAllLines(f.toPath())) {
            if (debug)
                System.out.println(line);
            boolean badLine = false;
            if (line.startsWith(prefix)) {
                line = line.substring(prefix.length());
                ArrayList<String> values = new ArrayList(numColumns);
                // Split first (n-1) values, consider the rest a single value.
                for (int i = 0; i < numColumns - 1; i++) {
                    int tabIdx = line.indexOf("\t");
                    if (tabIdx > 0) {
                        values.add(line.substring(0, tabIdx));
                        line = line.substring(tabIdx+1);
                    } else {
                        System.err.println("WARNING: malformed line: " + line);
                        badLine = true;
                        break;
                    }
                }
                if (!badLine) {
                    values.add(line);
                    proc.accept(values);
                }
            }
        }
    }
}
