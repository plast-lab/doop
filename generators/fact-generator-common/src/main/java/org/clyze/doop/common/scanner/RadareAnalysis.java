package org.clyze.doop.common.scanner;

import java.io.*;
import java.util.*;
import org.clyze.doop.common.Database;

// This class implements the analysis of the native scanner that uses Radare2.
class RadareAnalysis extends BinaryAnalysis {

    private static final String DOOP_HOME = "DOOP_HOME";
    private static final String doopHome = System.getenv(DOOP_HOME);
    private static final String LOC_MARKER = "STRING_LOC:";

    RadareAnalysis(Database db, String lib) {
        super(db, lib);
    }
    
    private static String getScript() {
        if (doopHome == null) {
            System.err.println("Cannot find Radare script, set environment variable " + DOOP_HOME);
            return null;
        } else
            return doopHome + "/bin/radare-strings.py";
    }

    /**
     * Use Radare (via external tool) to find strings.
     *
     * @return          the list of strings found
     */
    @Override
    public SortedMap<Long, String> findStrings() throws IOException {
        System.out.println("Finding strings with Radare2...");
        SortedMap<Long, String> strings = new TreeMap<>();

        String script = getScript();
        if (script == null)
            return strings;

        ProcessBuilder radareBuilder = new ProcessBuilder("rabin2", "-z", lib);
        System.out.println("Radare command line: " + radareBuilder.command());
        int lineNo = 0;
        final int IGNORE_ERRORS_BEFORE_LINE = 3;
        for (String line : NativeScanner.runCommand(radareBuilder)) {
            lineNo++;
            System.out.println(line);
            String[] parts = line.split("\\s+");
            if (parts.length < 7) {
                if (lineNo > IGNORE_ERRORS_BEFORE_LINE)
                    System.err.println("ERROR: cannot parse line " + lineNo + ": " + line);
                System.out.println("CONTINUE");
                continue;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 7; i < parts.length; i++)
                sb.append(parts[i]);
            try {
                long vaddr = hexToLong(parts[2]);
                System.out.println(parts[2] + " | " + vaddr + " | " + sb.toString());
                strings.put(vaddr, sb.toString());
            } catch (NumberFormatException ex) {
                if (lineNo > IGNORE_ERRORS_BEFORE_LINE) {
                    System.err.println("ERROR: cannot parse line " + lineNo + ": " + line);
                    ex.printStackTrace();
                }
                System.out.println("ERROR");
            }
        }
        System.err.println("Processed " + lineNo + " lines.");
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
    Map<String, Set<String>> findXRefs(Map<Long, String> binStrings) throws IOException {
        System.out.println("Finding string xrefs with Radare2 in: " + lib);

        Map<String, Set<String>> locs = new HashMap<>();

        String script = getScript();
        if (script == null)
            return locs;

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
                        if (Character.UnicodeBlock.of(c) != Character.UnicodeBlock.BASIC_LATIN)
                            nonLatin = true;
                    if (!nonLatin)
                        try {
                            writer.write(addr + " " + s + "\n");
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                });
        }

        File outFile = File.createTempFile("strings-out", ".txt");

        ProcessBuilder radareBuilder = new ProcessBuilder("python", script, lib, stringsFile.getCanonicalPath(), outFile.getCanonicalPath());
        System.out.println("Radare command line: " + radareBuilder.command());

        for (String line : NativeScanner.runCommand(radareBuilder)) {
            System.out.println(line);
            if (line.startsWith(LOC_MARKER)) {
                int tabIdx = line.indexOf("\t");
                if (tabIdx > 0) {
                    String func = line.substring(LOC_MARKER.length(), tabIdx);
                    String s = line.substring(tabIdx+1);
                    locs.computeIfAbsent(s, k -> new HashSet<>()).add(func);
                } else
                    System.err.println("WARNING: malformed line: " + line);
            }
        }
        return locs;
    }

    @Override
    public void initEntryPoints() throws IOException {
        System.err.println("TODO: entry points for Radare2");
    }


    @Override
    Set<Long> getGlobalDataPointers() throws IOException {
        System.err.println("TODO: global data pointers for Radare2");
        return new HashSet<>();
    }
}
