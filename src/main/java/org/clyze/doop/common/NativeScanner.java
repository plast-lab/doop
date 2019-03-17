package org.clyze.doop.common;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;
import static org.clyze.doop.common.PredicateFile.*;

public class NativeScanner {
    private final static boolean debug = false;
    private final static boolean check = false;
    private static final String envVar = "ANDROID_NDK_PREBUILTS";
    private static final String ndkPrebuilts = System.getenv(envVar);

    // The supported architectures.
    enum Arch {
        X86_64, AARCH64, ARMEABI;

        public static Arch autodetect(String libFilePath) throws IOException {
            ProcessBuilder pb = new ProcessBuilder("file", libFilePath);
            Arch arch = null;
            for (String line : NativeScanner.runCommand(pb)) {
                if (line.contains("80386") || line.contains("x86-64")) {
                    arch = Arch.X86_64;
                    break;
                } else if (line.contains("aarch64")) {
                    arch = Arch.AARCH64;
                    break;
                } else if (line.contains("ARM") || line.contains("EABI")) {
                    arch = Arch.ARMEABI;
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

    public static void scanLib(File libFile, File outDir) {
        String nmCmd = null;
        String objdumpCmd = null;
        try {
            // Auto-detect architecture.
            Arch arch = Arch.autodetect(libFile.getCanonicalPath());
            if (((arch == Arch.ARMEABI) || (arch == Arch.AARCH64)) && (ndkPrebuilts != null)) {
                nmCmd = ndkPrebuilts + "/nm";
                objdumpCmd = ndkPrebuilts + "/objdump";
            } else {
                nmCmd = "nm";
                objdumpCmd = "objdump";
            }
            scan(nmCmd, objdumpCmd, libFile, outDir, arch);
        } catch (IOException ex) {
            ex.printStackTrace();
        }

    }

    public static void scan(String nmCmd, String objdumpCmd,
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
        int prefixEndIdx;
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

    private static void processLib(String objdumpCmd, File outDir, String lib,
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

                    Map<Long, String> symbols = new HashMap<>();
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
        ProcessBuilder builderStrings = new ProcessBuilder("strings", lib);
        Collection<String> methodTypes = new LinkedList<>();
        Collection<String> names = new LinkedList<>();
        for (String line : runCommand(builderStrings)) {
            if (isMethodType(line))
                methodTypes.add(line);
            else if (isName(line))
                names.add(line);
        }
        int methodTypesCount = methodTypes.size();
        System.out.println("Possible method types found: " + methodTypesCount);
        int namesCount = names.size();
        System.out.println("Possible method/class names: " + namesCount);

        // Find in which function every string is used
        Map<String, List<String>> stringsInFunctions = null;

        try {
            stringsInFunctions = findStringsInFunctions(rodata.strings(), eps, lib, arch);
        } catch (Exception ex) {
            ex.printStackTrace();
            System.err.println("Cannot find strings in functions, aborting native scanner.");
            return;
        }

        // Write out facts.
        try (Database db = new Database(outDir)) {
            for (String mt : methodTypes) {
                List<String> strings = stringsInFunctions.get(mt);
                if (strings != null)
                    for (String function : strings)
                        db.add(NATIVE_METHODTYPE_CANDIDATE, lib, function, mt);
                else
                    db.add(NATIVE_METHODTYPE_CANDIDATE, lib, "-", mt);
            }

            for (String n : names) {
                List<String> strings = stringsInFunctions.get(n);
                if (strings != null)
                    for (String function : strings)
                        db.add(NATIVE_NAME_CANDIDATE, lib, function, n);
                else
                    db.add(NATIVE_NAME_CANDIDATE, lib, "-", n);
            }

            eps.forEach ((Long addr, String name) ->
                         db.add(NATIVE_LIB_ENTRY_POINT, name, String.valueOf(addr)));
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
                    System.err.println("Rejecting char '" + c + "' : " + line);
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
                    System.err.println("Rejecting char '" + c + "' : " + line);
                return false;
            }
        }
        return true;
    }

    static void processEntryPoint(SortedMap<Long, String> eps, Long addr) {
        System.out.println("[" + addr + "] " + eps.get(addr));
    }

    public static List<String> runCommand(ProcessBuilder builder) throws IOException {
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
     *  return in which functions every found string belongs
     **/
    private static Map<String,List<String>> findStringsInFunctions(Map<Long,String> foundStrings, Map<Long, String> eps, String lib, Arch arch) {
        if (arch.equals(Arch.X86_64))
            return findStringsInX86_64(foundStrings, eps, lib);
        else if (arch.equals(Arch.AARCH64))
            return findStringsInAARCH64(foundStrings, eps, lib);
        else if (arch.equals(Arch.ARMEABI)) {
            System.out.println("TODO: handling of gdb output: ");
            return new HashMap<>();
        }

        return null;
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
                        Long address = Long.parseLong(m.group(1),16);
                        String str = foundStrings.get(address);
                        if (debug)
                            System.out.println("gdb disassemble string: '" + str + "' -> " + address);
                        stringsInFunctions.computeIfAbsent(str, k -> new ArrayList<String>()).add(function);
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
        Matcher m = null;
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
                        stringsInFunctions.computeIfAbsent(str, k -> new ArrayList<String>()).add(function);
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
