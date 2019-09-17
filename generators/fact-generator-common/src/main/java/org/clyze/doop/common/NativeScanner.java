package org.clyze.doop.common;

import java.io.*;
import java.util.*;
import java.util.regex.*;

import static org.clyze.doop.common.PredicateFile.*;

public class NativeScanner {
    // Enable debug messages.
    private final static boolean debug = false;
    // Check for the presence of some special symbols (statistic).
    private final static boolean check = false;
    // Use Radare to find strings.
    private final boolean useRadare;
    // Parse .rodata section to find strings (imprecise, does not use
    // function boundaries or measure distances in the native code).
    private final static boolean parseRodata = true;

    // Environment variables needed to find external tools.
    private static final String envVarARMEABI = "ARMEABI_TOOLCHAIN";
    private static final String toolchainARMEABI = System.getenv(envVarARMEABI);
    private static final String envVarAARCH64 = "AARCH64_TOOLCHAIN";
    private static final String toolchainAARCH64 = System.getenv(envVarAARCH64);
    private static final String DOOP_HOME = "DOOP_HOME";
    private static final String doopHome = System.getenv(DOOP_HOME);

    // Dummy value for "function" column in facts.
    private static final String UNKNOWN_FUNCTION = "-";
    // Dummy value for "offset" column in facts.
    private static final String UNKNOWN_OFFSET = "-1";

    // The supported architectures.
    enum Arch {
        X86, X86_64, AARCH64, ARMEABI, MIPS;

        static Arch autodetect(String libFilePath) throws IOException {
            ProcessBuilder pb = new ProcessBuilder("file", libFilePath);
            Arch arch = null;
            for (String line : NativeScanner.runCommand(pb)) {
                if (line.contains("80386")) {
                    arch = Arch.X86;
                    break;
                } else if (line.contains("x86-64")) {
                    arch = Arch.X86_64;
                    break;
                } else if (line.contains("aarch64")) {
                    arch = Arch.AARCH64;
                    break;
                } else if (line.contains("ARM") || line.contains("EABI")) {
                    arch = Arch.ARMEABI;
                    break;
                } else if (line.contains("MIPS")) {
                    arch = Arch.MIPS;
                    break;
                }
            }
            if (arch != null)
                System.out.println("Detected architecture of " + libFilePath + " is " + arch);
            else {
                arch = NativeScanner.Arch.AARCH64;
                System.out.println("Could not determine architecture of " + libFilePath + ", using default: " + arch);
            }
            return arch;
        }
    }

    NativeScanner(boolean useRadare) {
        this.useRadare = useRadare;
    }

    /**
     * Scan a native code library.
     *
     * @param libFile        the native code library
     * @param outDir         the output directory to use to write facts
     */
    public void scanLib(File libFile, File outDir) {
        try {
            // Auto-detect architecture.
            Arch arch = Arch.autodetect(libFile.getCanonicalPath());
            String nmCmd = "nm";
            String objdumpCmd = "objdump";
            if (arch == Arch.ARMEABI) {
                if (toolchainARMEABI != null) {
                    nmCmd = toolchainARMEABI + "/bin/nm";
                    objdumpCmd = toolchainARMEABI + "/bin/objdump";
                } else
                    System.err.println("No ARMEABI toolchain found, set " + envVarARMEABI + ". Using system nm/objdump.");
            } else if (arch == Arch.AARCH64) {
                if (toolchainAARCH64 != null) {
                    nmCmd = toolchainAARCH64 + "/bin/nm";
                    objdumpCmd = toolchainAARCH64 + "/bin/objdump";
                } else
                    System.err.println("No AARCH64 toolchain found, set " + envVarAARCH64 + ". Using system nm/objdump.");
            }
            scan(nmCmd, objdumpCmd, libFile, outDir, arch);
        } catch (IOException ex) {
            ex.printStackTrace();
        }

    }

    /**
     * Scan a native code library.
     *
     * @param nmCmd          the path to tool 'nm'
     * @param objdumpCmd     the path to tool 'objdump'
     * @param libFile        the native code library
     * @param outDir         the output directory to use to write facts
     * @param arch           the architecture of the native code
     */
    private void scan(String nmCmd, String objdumpCmd,
                      File libFile, File outDir, Arch arch) {

        if (debug) {
            System.out.println("== Native scanner ==");
            System.out.println("arch = " + arch);
            System.out.println("nmCmd = " + nmCmd);
            System.out.println("objdumpCmd = " + objdumpCmd);
        }

        try {
            String lib = libFile.getCanonicalPath();
            System.out.println("== Processing library: " + lib + " ==");
            // Demangling interacts poorly with libraries lacking
            // symbol tables and is thus turned off.
            List<String> lines = parseLib(nmCmd, lib, false);
            if (check)
                checkSymbols(lines, lib);

            SortedMap<Long, String> libEntryPoints = new TreeMap<>();
            for (String line : lines) {
                EntryPoint ep = parseEntryPoint(line);
                if (ep != null)
                    libEntryPoints.put(ep.addr, ep.name);
            }
            processLib(objdumpCmd, outDir, lib, libEntryPoints, arch);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Reads all dynamic symbols from a library that contain the
     * substring "JNI". Matching results may be passed through
     * c++filt.
     *
     * @param nmCmd     the command to run the "nm" tool
     * @param lib       the path to the dynamic library
     * @param demangle  if true, nm does the demangling, otherwise
     *                  we use c++filt
     * @return          a list of lines containing entry points
     */
    private static List<String> parseLib(String nmCmd, String lib,
                                         boolean demangle) throws IOException {
        List<String> ids = new LinkedList<>();
        ProcessBuilder nmBuilder;
        if (demangle)
            nmBuilder = new ProcessBuilder(nmCmd, "--dynamic", "--demangle", lib);
        else
            nmBuilder = new ProcessBuilder(nmCmd, "--dynamic", lib);
        for (String nmLine : runCommand(nmBuilder)) {
            if (!nmLine.contains("JNI"))
                continue;
            ids.add(nmLine);
            // // Call separate tool to do name demangling.
            // final String CPPFILT = "c++filt";
            // ProcessBuilder cppfilt = new ProcessBuilder(CPPFILT, "'" + nmLine + "'");
            // List<String> lines = runCommand(cppfilt);
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

    private static String trimAfter(String str, String delim) {
        int delimIdx = str.indexOf(delim);
        int endIdx = delimIdx < 0 ? str.length() : delimIdx;
        return str.substring(0, endIdx);
    }

    private static EntryPoint parseEntryPoint(String line) {
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

    /**
     * Diagnostic: check for the presence of some special symbols in
     * the output of 'nm'.
     *
     * @param lines    the output lines
     * @param lib      the name of the library
     */
    private static void checkSymbols(Iterable<String> lines, String lib) {
        boolean referencesGetMethodID = false;
        boolean referencesGetFieldID = false;
        for (String line : lines) {
            if (debug)
                System.out.println("LINE: " + line);
            if (line.contains("W _JNIEnv::GetMethodID("))
                referencesGetMethodID = true;
            else if (line.contains("W _JNIEnv::GetFieldID("))
                referencesGetMethodID = true;
        }

        if (referencesGetMethodID)
            System.out.println("Library references GetMethodID(): " + lib);
        else if (referencesGetFieldID)
            System.out.println("Library references GetFieldID(): " + lib);
        else
            System.out.println("Library seems to not contain interesting JNIEnv calls: " + lib);
    }

    /**
     * Processes a native code library.
     *
     * @param objdumpCmd     the path to tool 'objdump'
     * @param outDir         the output directory to use to write facts
     * @param lib            the path of the library
     * @param eps            a map from offset to native code entry point
     * @param arch           the architecture of the native code
     */
    private void processLib(String objdumpCmd, File outDir, String lib,
                            Map<Long, String> eps, Arch arch) throws IOException {
        final String stringsSection = ".rodata";
        System.out.println("Finding " + stringsSection + " header");

        ProcessBuilder builder = new ProcessBuilder(objdumpCmd, "--headers", lib);
        int sizeIdx = -1;
        int offsetIdx = -1;
        Section rodata = null;

        List<String> lines = runCommand(builder);
        for (String line : lines) {
            // Autodetect column positions.
            if (sizeIdx == -1) {
                int sizeIdx0 = line.indexOf("Size ");
                if (sizeIdx0 != -1)
                    sizeIdx = sizeIdx0;
            }
            if (offsetIdx == -1) {
                int offsetIdx0 = line.indexOf("File off");
                if (offsetIdx0 != -1)
                    offsetIdx = offsetIdx0;
            }
            if (line.contains(stringsSection)) {
                if ((sizeIdx == -1) || (offsetIdx == -1)) {
                    System.err.println("Error, cannot find section " + stringsSection + " from output:");
                    for (String l : lines)
                        System.out.println(l);
                    return;
                } else {
                    int sizeEndIdx = line.indexOf(' ', sizeIdx);
                    int offsetEndIdx = line.indexOf(' ', offsetIdx);
                    int size = (int)Long.parseLong(line.substring(sizeIdx, sizeEndIdx), 16);
                    int offset = (int)Long.parseLong(line.substring(offsetIdx, offsetEndIdx), 16);
                    System.out.println(stringsSection + " section: offset = " + offset + ", size = " + size);

                    // Read section from the library.
                    RandomAccessFile raf = new RandomAccessFile(lib, "r");
                    raf.seek(offset);
                    byte[] bytes = new byte[size];
                    raf.readFully(bytes);

                    rodata = new Section(offset, size, bytes);
                    System.out.println("Section fully read.");
                    if (debug)
                        System.out.println(rodata.toString());

                    break;
                }
            }
        }

        if (rodata == null) {
            System.out.println("Library " + lib + " does not contain a " + stringsSection + " section.");
            return;
        }

        System.out.println("Gathering strings from " + lib + "...");

        // // Legacy 'strings'-based scanner.
        // ProcessBuilder builderStrings = new ProcessBuilder("strings", lib);
        // Collection<String> methodTypes = new LinkedList<>();
        // Collection<String> names = new LinkedList<>();
        // for (String line : runCommand(builderStrings)) {
        //     if (debug)
        //         System.out.println("Checking string: '" + line + "'");
        //     if (isMethodType(line)) {
        //         if (debug)
        //             System.out.println("isMethodType('" + line + "') = true");
        //         methodTypes.add(line);
        //     } else if (isName(line))
        //         names.add(line);
        // }

        // Find in which function every string is used
        Map<String, List<String>> stringsInFunctions = null;
        // Find radare strings
        List<String> stringsInRadare = null;

        boolean success = false;
        try {
            stringsInFunctions = findStringsInFunctions(objdumpCmd, rodata.strings(), eps, lib, arch);
            if (useRadare)
                stringsInRadare = findStringsInRadare(lib);
            if (stringsInFunctions != null || stringsInRadare != null)
                success = true;
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        if (debug && stringsInFunctions != null) {
            System.out.println("Strings in functions:");
            stringsInFunctions.forEach ((k, v) -> System.out.println("'" + k + "' -> " + v) );
        }

        if (!success) {
            System.err.println("Cannot find strings in " + lib + ", aborting.");
            return;
        }

        // Write out facts: first write names and method types that
        // belong to known functions, then write everything else (that
        // may be found via radare or parsing the .rodata section).
        Map<String, List<SymbolInfo>> nameSymbols = new HashMap<>();
        Map<String, List<SymbolInfo>> methodTypeSymbols = new HashMap<>();
        try (Database db = new Database(outDir)) {
            if (stringsInFunctions != null) {
                // For values that we know their containing function, we set special offsets
                for (String s : stringsInFunctions.keySet()) {
                    if (s == null)
                        continue;
                    if (isMethodType(s)) {
                        List<String> strings = stringsInFunctions.get(s);
                        if (strings != null)
                            for (String function : strings)
                                addSymbol(methodTypeSymbols, s, new SymbolInfo(lib, function, null));
                    } else if (isName(s)) {
                        List<String> strings = stringsInFunctions.get(s);
                        if (strings != null)
                            for (String function : strings)
                                addSymbol(nameSymbols, s, new SymbolInfo(lib, function, null));
                    }
                }
            }

            if (useRadare)
                for (int i = 0; i < stringsInRadare.size(); i++) {
                    String s = stringsInRadare.get(i);
                    if (isMethodType(s))
                        addSymbol(methodTypeSymbols, s, new SymbolInfo(lib, UNKNOWN_FUNCTION, (long) i));
                    else if (isName(s))
                        addSymbol(nameSymbols, s, new SymbolInfo(lib, UNKNOWN_FUNCTION, (long) i));
                }

            if (parseRodata)
                for (Map.Entry<Long, String> foundString : rodata.getFoundStrings().entrySet()) {
                    String s = foundString.getValue();
                    if (isMethodType(s))
                        addSymbol(methodTypeSymbols, s, new SymbolInfo(lib, UNKNOWN_FUNCTION, foundString.getKey()));
                    else if (isName(s))
                        addSymbol(nameSymbols, s, new SymbolInfo(lib, UNKNOWN_FUNCTION, foundString.getKey()));
                }

            // Write out symbol tables.
            int namesCount = nameSymbols.keySet().size();
            System.out.println("Possible method/class names: " + namesCount);
            writeSymbolTable(db, NATIVE_NAME_CANDIDATE, nameSymbols);
            int methodTypesCount = methodTypeSymbols.keySet().size();
            System.out.println("Possible method types found: " + methodTypesCount);
            writeSymbolTable(db, NATIVE_METHODTYPE_CANDIDATE, methodTypeSymbols);

            // Write out native code entry points.
            eps.forEach ((Long addr, String name) ->
                         db.add(NATIVE_LIB_ENTRY_POINT, lib, name, String.valueOf(addr)));
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

    static void processEntryPoint(SortedMap<Long, String> eps, Long addr) {
        System.out.println("[" + addr + "] " + eps.get(addr));
    }

    private static List<String> runCommand(ProcessBuilder builder) throws IOException {
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

    /**
     * Use Radare (via external tool) to find strings.
     *
     * @param lib    the path of the library
     * @return       the list of strings found
     */
    private static List<String> findStringsInRadare(String lib) {
        List<String> stringsInRadare = new ArrayList<>();
        if (doopHome == null) {
            System.err.println("Cannot find Radare script, set environment variable " + DOOP_HOME);
            return stringsInRadare;
        }
        try {
            ProcessBuilder radareBuilder = new ProcessBuilder("python", doopHome + "/bin/radare-strings.py", lib);
            for (String line : runCommand(radareBuilder)) {
                System.out.println(line);
                stringsInRadare.add(line);
            }
        } catch (IOException ex) {
            System.err.println("Could not run radare: " + ex.getMessage());
        }

        return stringsInRadare;
    }

    /**
     * Determine the function that contains every string found.
     *
     * @param objdumpCmd     the path to tool 'objdump'
     * @param foundStrings   a map from offset to string
     * @param eps            a map from offset to native code entry point
     * @param lib            the path of the library
     * @param arch           the architecture of the native code
     */
    private static Map<String,List<String>> findStringsInFunctions(String objdumpCmd, Map<Long,String> foundStrings, Map<Long, String> eps, String lib, Arch arch) {
        if (arch.equals(Arch.X86))
            return findStringsInX86(foundStrings, lib);
        else if (arch.equals(Arch.X86_64))
            return findStringsInX86_64(foundStrings, eps, lib);
        else if (arch.equals(Arch.AARCH64))
            return findStringsInAARCH64(foundStrings, eps, lib);
        else if (arch.equals(Arch.ARMEABI)) {
            // Fuse results for both armeabi/armeabi-v7a.
            Map<String, List<String>> eabi = findStringsInARMEABI(objdumpCmd, foundStrings, lib);
            Map<String, List<String>> eabi7 = findStringsInARMEABIv7a(objdumpCmd, foundStrings, lib);
            return mergeMaps(eabi, eabi7);
        }
        System.err.println("Architecture not supported: " + arch);
        return null;
    }

    /**
     * Merge two maps from keys to collections of values. Parameters may be mutated.
     */
    private static Map<String, List<String>> mergeMaps(Map<String, List<String>> map1,
                                                       Map<String, List<String>> map2) {
        for (Map.Entry<String, List<String>> entry : map2.entrySet()) {
            String key = entry.getKey();
            List<String> existing = map1.get(key);
            if (existing == null)
                map1.put(key, entry.getValue());
            else {
                List<String> newValue = map1.get(key);
                newValue.addAll(entry.getValue());
                map1.put(key, newValue);
            }
        }
        return map1;
    }

    private static Map<String,List<String>> findStringsInX86(Map<Long, String> foundStrings, String lib) {
        Long address;
        String function = null;
        Map<String,List<String>> stringsInFunctions = new HashMap<>();
        Map<String,Long> registers = null;
        Pattern funPattern = Pattern.compile("^.*[<](.*)[>][:]$");
        Pattern addPattern = Pattern.compile("^\\s+([a-f0-9]+).*add\\s+[$][0][x]([a-f0-9]+)[,][%](.*)$");
        Pattern leaPattern = Pattern.compile("^.*lea\\s+(.)[0][x]([a-f0-9]+)[(][%](.*)[)].*$");
        Matcher m;

        try {
            ProcessBuilder gdbBuilder = new ProcessBuilder("objdump", "-j", ".text", "-d", lib);
            for (String line : runCommand(gdbBuilder)) {
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
                            stringsInFunctions.computeIfAbsent(str, k -> new ArrayList<>()).add(function);
                        }
                    }
                }
            }
        } catch (IOException ex) {
            System.err.println("Could not run objdump: " + ex.getMessage());
        }

        return stringsInFunctions;
    }

    private static Map<String,List<String>> findStringsInX86_64(Map<Long,String> foundStrings, Map<Long, String> eps, String lib) {
        Map<String,List<String>> stringsInFunctions = new HashMap<>();
        Pattern leaPattern = Pattern.compile("^.*lea.*[#]\\s[0][x]([a-f0-9]+)$");
        for (Map.Entry<Long, String> entry : eps.entrySet()) {
            try {
                String function = entry.getValue();
                ProcessBuilder gdbBuilder = new ProcessBuilder("gdb", "-batch", "-ex", "disassemble " + function, lib);
                for (String line : runCommand(gdbBuilder)) {
                    Matcher m = leaPattern.matcher(line);
                    if (m.find()) {
                        long address = Long.parseLong(m.group(1),16);
                        String str = foundStrings.get(address);
                        if (debug)
                            System.out.println("gdb disassemble string: '" + str + "' -> " + address);
                        stringsInFunctions.computeIfAbsent(str, k -> new ArrayList<>()).add(function);
                    }
                }
            } catch (IOException ex) {
                System.err.println("Could not run gdb: " + ex.getMessage());
            }
        }
        return stringsInFunctions;
    }

    private static Map<String,List<String>> findStringsInAARCH64(Map<Long,String> foundStrings, Map<Long, String> eps, String lib) {
        Map<String,List<String>> stringsInFunctions = new HashMap<>();
        Pattern adrpPattern = Pattern.compile("^.*adrp\\s+([a-z0-9]+)[,]\\s[0][x]([a-f0-9]+)$");
        Pattern addPattern = Pattern.compile("^.*add\\s+([a-z0-9]+)[,]\\s([a-z0-9]+)[,]\\s[#][0][x]([a-f0-9]+)$");
        Pattern movPattern = Pattern.compile("^.*mov\\s+([a-z0-9]+)[,]\\s([a-z0-9]+)$");
        Matcher m;
        Map<String,String> registers = new HashMap<>();
        for (Map.Entry<Long, String> entry : eps.entrySet()) {
            try {
                String function = entry.getValue();
                ProcessBuilder gdbBuilder = new ProcessBuilder("gdb", "-batch", "-ex", "disassemble " + function, lib);
                for (String line : runCommand(gdbBuilder)) {
                    m = adrpPattern.matcher(line);
                    if (m.find())
                        registers.put(m.group(1),m.group(2));
                    m = addPattern.matcher(line);
                    if (m.find() && registers.containsKey(m.group(2))) {
                        Long address = Long.parseLong(registers.get(m.group(2)),16) + Long.parseLong(m.group(3),16);
                        String str = foundStrings.get(address);
                        if (debug)
                            System.out.println("gdb disassemble string: '" + str + "' -> " + registers.get(m.group(1)));
                        stringsInFunctions.computeIfAbsent(str, k -> new ArrayList<>()).add(function);
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

    private static Map<String,List<String>> findStringsInARMEABIv7a(String objdumpCmd, Map<Long,String> foundStrings, String lib) {
        String function = null;
        Pattern addrCodePattern = Pattern.compile("^\\s+([a-f0-9]+)[:]\\s+([a-f0-9]+)\\s?([a-f0-9]*)\\s+.*$");
        Pattern funPattern = Pattern.compile("^.*[<](.*)[>][:]$");
        Pattern insPattern = Pattern.compile("^\\s+([a-f0-9]+)[:]\\s+([a-f0-9]+)\\s?([a-f0-9]*)\\s+(\\w+[.]?\\w+)(.*)$");
        Pattern ldrPattern = Pattern.compile("^\\s+(\\w+)[,]\\s.*\\bpc.*[;]\\s[(]([a-f0-9]+).*$");
        Pattern ldrwPattern = Pattern.compile("^\\s+(\\w+)[,]\\s.*\\bpc.*[;]\\s([a-f0-9]+).*$");
        Pattern addPattern = Pattern.compile("^\\s(\\w+)[,]\\s(\\w+)[,]?\\s?(\\w*)(.*)$");
        Pattern movPattern = Pattern.compile("^\\s(\\w+)[,]\\s(\\w+)$");
        Matcher m;
        Map<String,String> registers = null, addressCode = new HashMap<>();
        Map<String,List<String>> stringsInFunctions = new HashMap<>();

        ProcessBuilder objdumpBuilder = new ProcessBuilder(objdumpCmd, "-j", ".text", "-d", lib);
        try {
            for (String line : runCommand(objdumpBuilder)) {
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
            for (String line : runCommand(objdumpBuilder)) {
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
                                stringsInFunctions.computeIfAbsent(str, k -> new ArrayList<>()).add(function);
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

    private static Map<String,List<String>> findStringsInARMEABI(String objdumpCmd, Map<Long,String> foundStrings, String lib) {
        String function = null;
        Pattern funPattern = Pattern.compile(".*[<](.*)[>][:]$");
        Pattern insPattern = Pattern.compile("^\\s([a-f0-9]+)[:]\\s+([a-f0-9]+)\\s+[.]?(\\w+)(.*)$");
        Pattern ldrPattern = Pattern.compile("^\\s+(\\w+).*\\bpc.*[;]\\s([a-f0-9]+).*$");
        Pattern addPattern = Pattern.compile("^\\s+(\\w+)[,]\\s(\\w+)[,]\\s(\\w+)$");
        Pattern movPattern = Pattern.compile("^\\s+(\\w+)[,]\\s(\\w+)$");
        Matcher m;
        Map<String,String> registers = null, words = new HashMap<>();
        Map<String,List<String>> stringsInFunctions = new HashMap<>();

        ProcessBuilder objdumpBuilder = new ProcessBuilder(objdumpCmd, "-j", ".text", "-d", lib);
        try {
            for (String line : runCommand(objdumpBuilder)) {
                m = insPattern.matcher(line);
                if (m.find() && m.group(3).equals("word"))
                    words.put(m.group(1),m.group(2));
            }
            for (String line : runCommand(objdumpBuilder)) {
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
                                    stringsInFunctions.computeIfAbsent(str, k -> new ArrayList<>()).add(function);
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

    // Handle .xzs libraries (found in some .apk inputs).
    public void scanXZSLib(File xzsFile, File outDir) {
        String xzsPath = xzsFile.getAbsolutePath();
        System.out.println("Processing xzs-packed native code: " + xzsPath);
        String xzPath = xzsPath.substring(0, xzsPath.length() - 1);
        try {
            // Change .xzs extension to .xz.
            runCommand(new ProcessBuilder("mv", xzsPath, xzPath));
            runCommand(new ProcessBuilder("xz", "--decompress", xzPath));
            File libTmpFile = new File(xzPath.substring(0, xzPath.length() - 3));
            scanLib(libTmpFile, outDir);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    // Handle .zstd libraries (found in some .apk inputs).
    public void scanZSTDLib(File zstdFile, File outDir) {
        String zstdPath = zstdFile.getAbsolutePath();
        System.out.println("Processing zstd-packed native code: " + zstdPath);
        String zstdOutPath = zstdPath.substring(0, zstdPath.length() - 5);
        try {
            runCommand(new ProcessBuilder("zstd", "-d", "-o", zstdOutPath));
            File libTmpFile = new File(zstdOutPath);
            scanLib(libTmpFile, outDir);
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
        List<SymbolInfo> infos = symbols.getOrDefault(symbol, new LinkedList<>());
        infos.add(si);
        symbols.put(symbol, infos);
    }

    /**
     * Write the full symbol table. This method can also be extended
     * to merge information per symbol (for example, if different
     * entries contain complementary information).
     *
     * @param db         the database object to use
     * @param factsFile  the facts file to use for writing
     * @param symbols    the symbols table
     */
    private static void writeSymbolTable(Database db, PredicateFile factsFile,
                                         Map<String, List<SymbolInfo> > symbols) {
        for (Map.Entry<String, List<SymbolInfo>> entry : symbols.entrySet()) {
            String symbol = entry.getKey();
            for (SymbolInfo si : entry.getValue()) {
                String offset = si.offset == null ? UNKNOWN_OFFSET : Long.toString(si.offset);
                db.add(factsFile, si.lib, si.function, symbol, offset);
            }
        }
    }
}

// A representation of the strings section in the binary.
class Section {
    private final int offset;
    private final int size;
    private final byte[] data;
    private Map<Long, String> foundStrings;

    public Section(int offset, int size, byte[] data) {
        this.offset = offset;
        this.size = size;
        this.data = data;
    }

    public Map<Long,String> getFoundStrings() {
        return foundStrings;
    }

    /**
     * Scan the 'data' buffer for NULL-terminated strings.
     *
     * @return a collection of the strings found
     */
    Map<Long, String> strings() {
        if (this.foundStrings == null) {
            this.foundStrings = new TreeMap<>();
            StringBuilder foundString = new StringBuilder();
            long addr = offset;
            for (int i = 0; i < data.length; i++)
                if (data[i] == 0) {
                    if (!foundString.toString().equals("")) {
                        foundStrings.put(addr, foundString.toString());
                        foundString = new StringBuilder();
                    }
                    addr = offset + i + 1;
                } else
                    foundString.append((char) data[i]);
        }
        return this.foundStrings;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("Section [offset = " + offset + ", size = " + size + "]\n");
        strings().forEach((Long addr, String s) -> sb.append(addr).append(": String '").append(s).append("'\n"));
        return sb.toString();
    }
}

class EntryPoint {
    final String name;
    final Long addr;
    public EntryPoint(String name, Long addr) {
        this.name = name;
        this.addr = addr;
    }
}

class SymbolInfo {
    final String lib;
    final String function;
    final Long offset;
    SymbolInfo(String lib, String function, Long offset) {
        this.lib = lib;
        this.function = function;
        this.offset = offset;
    }
}
