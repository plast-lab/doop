package org.clyze.doop.common.scanner;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import org.clyze.doop.common.Database;

// This class implements the analysis of the native scanner that uses
// binutils (such as 'objdump' and 'nm').
class BinutilsAnalysis extends BinaryAnalysis {

    // Enable debug messages.
    private final static boolean debug = false;
    // Check for the presence of some special symbols (statistic).
    private final static boolean check = false;
    // Environment variables needed to find external tools.
    private static final String envVarARMEABI = "ARMEABI_TOOLCHAIN";
    private static final String toolchainARMEABI = System.getenv(envVarARMEABI);
    private static final String envVarAARCH64 = "AARCH64_TOOLCHAIN";
    private static final String toolchainAARCH64 = System.getenv(envVarAARCH64);

    // The native code architecture.
    private Arch arch;
    // The path to tool 'nm'.
    private String nmCmd;
    // The path to tool 'objdump'.
    private String objdumpCmd;
    // The sections that may contain information about strings and global data structures.
    private Section data;
    private Section rodata;

    BinutilsAnalysis(Database db, String lib) {
        super(db, lib);

        // Auto-detect architecture.
        try {
            this.arch = Arch.autodetect(lib);
        } catch (IOException ex) {
            this.arch = Arch.DEFAULT_ARCH;
        }

        this.nmCmd = "nm";
        this.objdumpCmd = "objdump";
        if (arch == Arch.ARMEABI) {
            if (toolchainARMEABI != null) {
                this.nmCmd = toolchainARMEABI + "/bin/nm";
                this.objdumpCmd = toolchainARMEABI + "/bin/objdump";
            } else
                System.err.println("No ARMEABI toolchain found, set " + envVarARMEABI + ". Using system nm/objdump.");
        } else if (arch == Arch.AARCH64) {
            if (toolchainAARCH64 != null) {
                this.nmCmd = toolchainAARCH64 + "/bin/nm";
                this.objdumpCmd = toolchainAARCH64 + "/bin/objdump";
            } else
                System.err.println("No AARCH64 toolchain found, set " + envVarAARCH64 + ". Using system nm/objdump.");
        }

        if (debug) {
            System.out.println("== Native scanner paths ==");
            System.out.println("arch = " + arch);
            System.out.println("nmCmd = " + nmCmd);
            System.out.println("objdumpCmd = " + objdumpCmd);
        }
    }

    @Override
    public Map<String, Set<String>> findXRefs(Map<Long, String> binStrings) {
        System.out.println("Using built-in scanner to find strings in functions...");
        if (arch.equals(Arch.X86))
            return findXRefsInX86(binStrings, lib);
        else if (arch.equals(Arch.X86_64))
            return findXRefsInX86_64(binStrings, lib);
        else if (arch.equals(Arch.AARCH64))
            return findXRefsInAARCH64(binStrings, lib);
        else if (arch.equals(Arch.ARMEABI)) {
            // Fuse results for both armeabi/armeabi-v7a.
            Map<String, Set<String>> eabi = findXRefsInARMEABI(binStrings, lib);
            Map<String, Set<String>> eabi7 = findXRefsInARMEABIv7a(binStrings, lib);
            return mergeMaps(eabi, eabi7);
        }
        System.err.println("Architecture not supported: " + arch);
        return null;
    }

    /**
     * Merge two maps from keys to collections of values. Parameters may be mutated.
     */
    private static Map<String, Set<String>> mergeMaps(Map<String, Set<String>> map1,
                                                      Map<String, Set<String>> map2) {
        for (Map.Entry<String, Set<String>> entry : map2.entrySet()) {
            String key = entry.getKey();
            Set<String> existing = map1.get(key);
            if (existing == null)
                map1.put(key, entry.getValue());
            else {
                Set<String> newValue = map1.get(key);
                newValue.addAll(entry.getValue());
                map1.put(key, newValue);
            }
        }
        return map1;
    }

    private Map<String, Set<String>> findXRefsInX86(Map<Long, String> foundStrings, String lib) {
        Long address;
        String function = null;
        Map<String, Set<String>> stringsInFunctions = new HashMap<>();
        Map<String, Long> registers = null;
        Pattern funPattern = Pattern.compile("^.*[<](.*)[>][:]$");
        Pattern addPattern = Pattern.compile("^\\s+([a-f0-9]+).*add\\s+[$][0][x]([a-f0-9]+)[,][%](.*)$");
        Pattern leaPattern = Pattern.compile("^.*lea\\s+(.)[0][x]([a-f0-9]+)[(][%](.*)[)].*$");
        Matcher m;

        try {
            ProcessBuilder gdbBuilder = new ProcessBuilder("objdump", "-j", ".text", "-d", lib);
            for (String line : NativeScanner.runCommand(gdbBuilder)) {
                m = funPattern.matcher(line);
                if (m.find()) {
                    function = m.group(1);
                    registers = new HashMap<>();
                    continue;
                }

                m = addPattern.matcher(line);
                if (m.find()) {
                    Long value = Long.parseLong(m.group(1),16) + Long.parseLong(m.group(2),16);
                    if (registers == null)
                        System.err.println("WARNING: no registers map initialized for 'add' pattern");
                    else
                        registers.put(m.group(3), value);
                    continue;
                }

                m = leaPattern.matcher(line);
                if (m.find()) {
                    if (registers == null)
                        System.err.println("WARNING: no registers map initialized for 'lea' pattern");
                    else if (registers.get(m.group(3)) != null) {
                        address = registers.get(m.group(3));
                        if (m.group(1).equals(" "))
                            address += Long.parseLong(m.group(2),16);
                        else if (m.group(1).equals("-"))
                            address -= Long.parseLong(m.group(2),16);

                        if (foundStrings.get(address) != null) {
                            String str = foundStrings.get(address);
                            if (debug)
                                System.out.println("objdump disassemble string: '" + str + "' -> " + address);
                            stringsInFunctions.computeIfAbsent(str, k -> new HashSet<>()).add(function);
                        }
                    }
                }
            }
        } catch (IOException ex) {
            System.err.println("Could not run objdump: " + ex.getMessage());
        }

        return stringsInFunctions;
    }

    private Map<String, Set<String>> findXRefsInX86_64(Map<Long,String> foundStrings, String lib) {
        Map<String, Set<String>> stringsInFunctions = new HashMap<>();
        Pattern leaPattern = Pattern.compile("^.*lea.*[#]\\s[0][x]([a-f0-9]+)$");
        for (Map.Entry<Long, String> entry : entryPoints.entrySet()) {
            try {
                String function = entry.getValue();
                ProcessBuilder gdbBuilder = new ProcessBuilder("gdb", "-batch", "-ex", "disassemble " + function, lib);
                for (String line : NativeScanner.runCommand(gdbBuilder)) {
                    Matcher m = leaPattern.matcher(line);
                    if (m.find()) {
                        long address = Long.parseLong(m.group(1),16);
                        String str = foundStrings.get(address);
                        if (debug)
                            System.out.println("gdb disassemble string: '" + str + "' -> " + address);
                        stringsInFunctions.computeIfAbsent(str, k -> new HashSet<>()).add(function);
                    }
                }
            } catch (IOException ex) {
                System.err.println("Could not run gdb: " + ex.getMessage());
            }
        }
        return stringsInFunctions;
    }

    private Map<String, Set<String>> findXRefsInAARCH64(Map<Long,String> foundStrings, String lib) {
        Map<String, Set<String>> stringsInFunctions = new HashMap<>();
        Pattern adrpPattern = Pattern.compile("^.*adrp\\s+([a-z0-9]+)[,]\\s[0][x]([a-f0-9]+)$");
        Pattern addPattern = Pattern.compile("^.*add\\s+([a-z0-9]+)[,]\\s([a-z0-9]+)[,]\\s[#][0][x]([a-f0-9]+)$");
        Pattern movPattern = Pattern.compile("^.*mov\\s+([a-z0-9]+)[,]\\s([a-z0-9]+)$");
        Matcher m;
        Map<String,String> registers = new HashMap<>();
        for (Map.Entry<Long, String> entry : entryPoints.entrySet()) {
            try {
                String function = entry.getValue();
                ProcessBuilder gdbBuilder = new ProcessBuilder("gdb", "-batch", "-ex", "disassemble " + function, lib);
                for (String line : NativeScanner.runCommand(gdbBuilder)) {
                    m = adrpPattern.matcher(line);
                    if (m.find())
                        registers.put(m.group(1),m.group(2));
                    m = addPattern.matcher(line);
                    if (m.find() && registers.containsKey(m.group(2))) {
                        Long address = Long.parseLong(registers.get(m.group(2)),16) + Long.parseLong(m.group(3),16);
                        String str = foundStrings.get(address);
                        if (debug)
                            System.out.println("gdb disassemble string: '" + str + "' -> " + registers.get(m.group(1)));
                        stringsInFunctions.computeIfAbsent(str, k -> new HashSet<>()).add(function);
                    }
                    m = movPattern.matcher(line);
                    if (m.find() && registers.containsKey(m.group(2)))
                        registers.put(m.group(1),registers.get(m.group(2)));
                }
            } catch (IOException ex) {
                System.err.println("Could not run gdb: " + ex.getMessage());
            }
        }
        return stringsInFunctions;
    }

    private Map<String, Set<String>> findXRefsInARMEABIv7a(Map<Long, String> foundStrings, String lib) {
        String function = null;
        Pattern addrCodePattern = Pattern.compile("^\\s+([a-f0-9]+)[:]\\s+([a-f0-9]+)\\s?([a-f0-9]*)\\s+.*$");
        Pattern funPattern = Pattern.compile("^.*[<](.*)[>][:]$");
        Pattern insPattern = Pattern.compile("^\\s+([a-f0-9]+)[:]\\s+([a-f0-9]+)\\s?([a-f0-9]*)\\s+(\\w+[.]?\\w+)(.*)$");
        Pattern ldrPattern = Pattern.compile("^\\s+(\\w+)[,]\\s.*\\bpc.*[;]\\s[(]([a-f0-9]+).*$");
        Pattern ldrwPattern = Pattern.compile("^\\s+(\\w+)[,]\\s.*\\bpc.*[;]\\s([a-f0-9]+).*$");
        Pattern addPattern = Pattern.compile("^\\s(\\w+)[,]\\s(\\w+)[,]?\\s?(\\w*)(.*)$");
        Pattern movPattern = Pattern.compile("^\\s(\\w+)[,]\\s(\\w+)$");
        Matcher m;
        Map<String, String> registers = null, addressCode = new HashMap<>();
        Map<String, Set<String>> stringsInFunctions = new HashMap<>();

        ProcessBuilder objdumpBuilder = new ProcessBuilder(objdumpCmd, "-j", ".text", "-d", lib);
        try {
            for (String line : NativeScanner.runCommand(objdumpBuilder)) {
                m = addrCodePattern.matcher(line);
                if (m.find()) {
                    if (!m.group(3).equals("")) {
                        String nextAddr = Integer.toHexString(Integer.parseInt(m.group(1),16)+Integer.parseInt("2",16));
                        addressCode.put(m.group(1),m.group(2));
                        addressCode.put(nextAddr,m.group(3));
                    } else {
                        if (m.group(2).length()==4)
                            addressCode.put(m.group(1),m.group(2));
                        else {
                            addressCode.put(m.group(1),m.group(2).substring(0,4));
                            String nextAddr = Integer.toHexString(Integer.parseInt(m.group(1),16)+Integer.parseInt("2",16));
                            addressCode.put(nextAddr, m.group(2).substring(4,8));
                        }
                    }
                }
            }
            for (String line : NativeScanner.runCommand(objdumpBuilder)) {
                m = funPattern.matcher(line);
                if (m.find()) {
                    function = m.group(1);
                    if (function.contains("@"))
                        function = function.substring(0, function.indexOf('@'));
                    registers = new HashMap<>();
                    if (debug)
                        System.out.println("new function " + function);
                    continue;
                }
                try {
                    m = insPattern.matcher(line);
                    if (m.find()) {
                        if (registers == null) {
                            System.err.println("WARNING: no registers map initialized for 'ins' pattern");
                            continue;
                        }
                        registers.put("pc",m.group(1));
                        String instruction = m.group(5);
                        if (m.group(4).equals("ldr")) {
                            m = ldrPattern.matcher(instruction);
                            if (m.find()) {
                                String addr = m.group(2);
                                String nextAddr = Integer.toHexString(Integer.parseInt(addr,16)+Integer.parseInt("2",16));
                                String value;
                                if (addressCode.containsKey(nextAddr))
                                    value = addressCode.get(nextAddr)+addressCode.get(addr);
                                else
                                    value = addressCode.get(addr);
                                registers.put(m.group(1), value);
                            }
                        } else if (m.group(4).equals("ldr.w")) {
                            m = ldrwPattern.matcher(instruction);
                            if (m.find()) {
                                String addr = m.group(2);
                                String nextAddr = Integer.toHexString(Integer.parseInt(addr,16)+Integer.parseInt("2",16));
                                String value;
                                if (addressCode.containsKey(nextAddr))
                                    value = addressCode.get(nextAddr)+addressCode.get(addr);
                                else
                                    value = addressCode.get(addr);
                                registers.put(m.group(1), value);
                            }
                        } else if (m.group(4).contains("add") || m.group(4).equals("adr")) {
                            m = addPattern.matcher(instruction);
                            if (m.find() && registers.containsKey(m.group(1)) && registers.containsKey(m.group(2))) {
                                long address = Long.parseLong(registers.get(m.group(2)), 16);
                                if (!m.group(3).equals("")) {
                                    if (!registers.containsKey(m.group(3)))
                                        if (m.group(4).contains("#"))
                                            address += Long.parseLong(m.group(4).substring(m.group(4).lastIndexOf('#')),16);
                                        else
                                            continue;
                                    address += Long.parseLong(registers.get(m.group(3)), 16);
                                } else
                                    address += Long.parseLong(registers.get(m.group(1)), 16);
                                int len = Long.toHexString(address).length();
                                if (len>registers.get(m.group(1)).length() && len>registers.get(m.group(2)).length())
                                    address = Long.parseLong(Long.toHexString(address).substring(1),16);
                                registers.put(m.group(1),Long.toHexString(address));
                                address += Long.parseLong("4",16);
                                String str = foundStrings.get(address);
                                if (debug)
                                    System.out.println("objdump disassemble string: '" + str + "' -> " + registers.get(m.group(1)));
                                stringsInFunctions.computeIfAbsent(str, k -> new HashSet<>()).add(function);
                            }
                        } else if (m.group(4).equals("mov")) {
                            m = movPattern.matcher(instruction);
                            if (m.find() && registers.containsKey(m.group(2)))
                                registers.put(m.group(1),registers.get(m.group(2)));
                        }
                    }
                } catch (NumberFormatException ex) {
                    System.err.println("Number format error '" + ex.getMessage() + "' in line: " + line);
                }
            }
        } catch (IOException ex) {
            System.err.println("Could not run objdump: " + ex.getMessage());
        }
        return stringsInFunctions;
    }

    private Map<String, Set<String>> findXRefsInARMEABI(Map<Long,String> foundStrings, String lib) {
        String function = null;
        Pattern funPattern = Pattern.compile(".*[<](.*)[>][:]$");
        Pattern insPattern = Pattern.compile("^\\s([a-f0-9]+)[:]\\s+([a-f0-9]+)\\s+[.]?(\\w+)(.*)$");
        Pattern ldrPattern = Pattern.compile("^\\s+(\\w+).*\\bpc.*[;]\\s([a-f0-9]+).*$");
        Pattern addPattern = Pattern.compile("^\\s+(\\w+)[,]\\s(\\w+)[,]\\s(\\w+)$");
        Pattern movPattern = Pattern.compile("^\\s+(\\w+)[,]\\s(\\w+)$");
        Matcher m;
        Map<String, String> registers = null, words = new HashMap<>();
        Map<String, Set<String>> stringsInFunctions = new HashMap<>();

        ProcessBuilder objdumpBuilder = new ProcessBuilder(objdumpCmd, "-j", ".text", "-d", lib);
        try {
            for (String line : NativeScanner.runCommand(objdumpBuilder)) {
                m = insPattern.matcher(line);
                if (m.find() && m.group(3).equals("word"))
                    words.put(m.group(1),m.group(2));
            }
            for (String line : NativeScanner.runCommand(objdumpBuilder)) {
                m = funPattern.matcher(line);
                if (m.find()) {
                    function = m.group(1);
                    registers = new HashMap<>();
                    //System.out.println("new function " + function);
                    continue;
                }
                m = insPattern.matcher(line);
                if (m.find()) {
                    if (registers == null) {
                        System.err.println("WARNING: no registers map initialized for 'ins' pattern");
                        continue;
                    }
                    registers.put("pc",m.group(1));
                    String instruction = m.group(4);
                    switch (m.group(3)) {
                        case "ldr":
                            m = ldrPattern.matcher(instruction);
                            if (m.find())
                                registers.put(m.group(1), words.get(m.group(2)));
                            break;
                        case "add":
                            m = addPattern.matcher(instruction);
                            if (m.find() && registers.containsKey(m.group(2)) && registers.containsKey(m.group(3))) {
                                try {
                                    long address = Long.parseLong(registers.get(m.group(2)), 16) + Long.parseLong("8", 16);
                                    address += Long.parseLong(registers.get(m.group(3)), 16);
                                    String str = foundStrings.get(address);
                                    if (debug)
                                        System.out.println("objdump disassemble string: '" + str + "' -> " + registers.get(m.group(1)));
                                    stringsInFunctions.computeIfAbsent(str, k -> new HashSet<>()).add(function);
                                } catch (NumberFormatException ex) {
                                    System.err.println("Number format error '" + ex.getMessage() + "' in line: " + line);
                                }
                            }
                            break;
                        case "mov":
                            m = movPattern.matcher(instruction);
                            if (m.find() && registers.containsKey(m.group(2)))
                                registers.put(m.group(1), registers.get(m.group(2)));
                            break;
                    }
                }
            }
        } catch (IOException ex) {
            System.err.println("Could not run objdump: " + ex.getMessage());
        }
        return stringsInFunctions;
    }

    @Override
    public SortedMap<Long, String> findStrings() throws IOException {
        if (rodata == null)
            analyzeSections();
        return rodata == null ? null : rodata.strings();
    }

    @Override
    public Set<Long> getGlobalDataPointers() throws IOException {
        if (data == null)
            analyzeSections();
        return data == null ? null : data.analyzeWords();
    }

    private void analyzeSections() throws IOException {
        System.out.println("Reading section headers...");
        ProcessBuilder builder = new ProcessBuilder(objdumpCmd, "--headers", lib);
        List<String> lines = NativeScanner.runCommand(builder);
        this.rodata = Section.fromObjdump(arch, lib, ".rodata", lines);
        if (debug)
            System.out.println(rodata.toString());

        this.data = Section.fromObjdump(arch, lib, ".data", lines);
    }

    // @Override
    // public SortedMap<Long, String> findStrings() {
    //     // // Legacy 'strings'-based scanner.
    //     // ProcessBuilder builderStrings = new ProcessBuilder("strings", lib);
    //     // Collection<String> methodTypes = new LinkedList<>();
    //     // Collection<String> names = new LinkedList<>();
    //     // for (String line : runCommand(builderStrings)) {
    //     //     if (debug)
    //     //         System.out.println("Checking string: '" + line + "'");
    //     //     if (isMethodType(line)) {
    //     //         if (debug)
    //     //             System.out.println("isMethodType('" + line + "') = true");
    //     //         methodTypes.add(line);
    //     //     } else if (isName(line))
    //     //         names.add(line);
    //     // }
    // }

    /**
     * Reads dynamic symbols from a library. Matching results may be
     * passed through c++filt.
     *
     * @param lib       the path to the dynamic library
     * @param demangle  if true, nm does the demangling, otherwise
     *                  we use c++filt
     * @return          a list of lines containing entry points
     */
    private List<String> libSymbols(String lib, boolean demangle) throws IOException {
        List<String> ids = new LinkedList<>();

        List<String> nmInvocation = new ArrayList<>();
        nmInvocation.add(nmCmd);
        nmInvocation.add("--defined-only");
        nmInvocation.add("--dynamic");
        if (demangle)
            nmInvocation.add("--demangle");
        nmInvocation.add(lib);

        ProcessBuilder nmBuilder = new ProcessBuilder(nmInvocation);
        for (String nmLine : NativeScanner.runCommand(nmBuilder)) {
            // if (!nmLine.contains("JNI"))
            //     continue;
            ids.add(nmLine);
            // // Call separate tool to do name demangling.
            // final String CPPFILT = "c++filt";
            // ProcessBuilder cppfilt = new ProcessBuilder(CPPFILT, "'" + nmLine + "'");
            // List<String> lines = NativeScanner.runCommand(cppfilt);
            // if (lines.size() == 1)
            //     ids.add(lines.get(0));
            // else {
            //     String out = lines.stream().map(Object::toString).collect(Collectors.joining(", "));
            //     System.err.println("Error: cannot process " + CPPFILT + " output: " + out);
            //     // Add original line.
            //     ids.add(nmLine);
            // }
        }
        return ids;
    }

    /**
     * Diagnostic: check for the presence of some special symbols in
     * the output of 'nm'.
     *
     * @param symbols  the text output of 'nm' that contains symbols
     * @param lib      the name of the library
     */
    private static void checkSymbols(Iterable<String> symbols, String lib) {
        boolean referencesGetMethodID = false;
        boolean referencesGetFieldID = false;
        for (String symbol : symbols) {
            if (debug)
                System.out.println("SYMBOL: " + symbol);
            if (symbol.contains("W _JNIEnv::GetMethodID("))
                referencesGetMethodID = true;
            else if (symbol.contains("W _JNIEnv::GetFieldID("))
                referencesGetMethodID = true;
        }

        if (referencesGetMethodID)
            System.out.println("Library references GetMethodID(): " + lib);
        else if (referencesGetFieldID)
            System.out.println("Library references GetFieldID(): " + lib);
        else
            System.out.println("Library seems to not contain interesting JNIEnv calls: " + lib);
    }

    @Override
    public void initEntryPoints() throws IOException {
        // Demangling interacts poorly with libraries lacking
        // symbol tables and is thus turned off.
        List<String> symbols = libSymbols(lib, false);
        if (check)
            checkSymbols(symbols, lib);
        for (String symbol : symbols) {
            EntryPoint ep = EntryPoint.fromNmOutput(symbol);
            if (ep != null)
                entryPoints.put(ep.addr, ep.name);
        }
    }
}

class EntryPoint {
    final String name;
    final Long addr;
    private EntryPoint(String name, Long addr) {
        this.name = name;
        this.addr = addr;
    }

    private static String trimAfter(String str, String delim) {
        int delimIdx = str.indexOf(delim);
        int endIdx = delimIdx < 0 ? str.length() : delimIdx;
        return str.substring(0, endIdx);
    }

    static EntryPoint fromNmOutput(String line) {
        String prefix = line;
        // Cut part after left parenthesis.
        prefix = trimAfter(prefix, "(");
        // Cut part after left bracket.
        prefix = trimAfter(prefix, "<");

        int lastSpaceIndex = prefix.lastIndexOf(" ");
        if (lastSpaceIndex == -1) {
            System.err.println("Error: cannot determine format of symbols output.");
            return null;
        } else if (prefix.charAt(lastSpaceIndex - 1) == 'U') {
            System.out.println("Ignoring line containing: " + prefix);
            return null;
        }

        int firstSpaceIndex = prefix.indexOf(" ");
        long addr = -1;
        if (firstSpaceIndex != -1) {
            String field = prefix.substring(0, firstSpaceIndex);
            if (field.charAt(0) == '\'')
                field = field.substring(1);
            try {
                addr = Long.parseLong(field, 16);
            } catch (NumberFormatException ex) {
                System.err.println("Cannot compute address[0.." + firstSpaceIndex + "] for field: " + field);
            }
        }

        String method = prefix.substring(lastSpaceIndex + 1);
        if (method.startsWith("_JNIEnv::"))
            return null;
        else if (method.equals(""))
            throw new RuntimeException("Empty method! line = " + line + ", prefix = " + prefix);
        else
            return new EntryPoint(method, addr);
    }
}
