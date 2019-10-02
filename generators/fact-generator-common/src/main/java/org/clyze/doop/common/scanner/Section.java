package org.clyze.doop.common.scanner;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.*;

// A representation of a section in a binary.
class Section {
    private final Arch arch;
    private final int size;
    private final long vma;
    private final long offset;
    private final byte[] data;
    private SortedMap<Long, String> foundStrings;
    private final Set<Long> words;

    private Section(Arch arch, int size, long vma, long offset, byte[] data) {
        this.arch = arch;
        this.size = size;
        this.vma = vma;
        this.offset = offset;
        this.data = data;
        this.words = new HashSet<>();
    }

    /*
     * Object builder from objdump output.
     *
     * @param arch         the library architecture
     * @param lib          the library path
     * @param sectionName  the name of the section
     * @param lines        the text output of objdump
     * @return             a section object or null if no section was found
     */
    public static Section fromObjdump(Arch arch, String lib,
                                      String sectionName, Iterable<String> lines)
        throws IOException {

        int lineNo = 0;
        final int IGNORE_ERRORS_BEFORE_LINE = 3;
        for (String line : lines) {
            if (!line.contains(sectionName + " "))
                continue;
            String[] parts = line.trim().split("\\s+");
            if (parts.length < 7)
                continue;
            try {
                String secName = parts[1];
                System.out.println(secName + ": " + secName.trim().equals(sectionName));
                int size = BinaryAnalysis.hexToInt(parts[2]);
                long vma = BinaryAnalysis.hexToLong(parts[3]);
                long offset = BinaryAnalysis.hexToLong(parts[5]);
                System.out.println(sectionName + " section: offset = " + offset + ", size = " + size + ", vma = " + vma);

                // Read section from the library.
                RandomAccessFile raf = new RandomAccessFile(lib, "r");
                raf.seek(offset);
                byte[] bytes = new byte[size];
                raf.readFully(bytes);

                System.out.println("Section fully read: " + sectionName);
                return new Section(arch, size, vma, offset, bytes);
            } catch (NumberFormatException ex) {
            }

        }

        System.err.println("Error, cannot find section " + sectionName + " from output:");
        for (String l : lines)
            System.out.println(l);
        return null;
    }

    /**
     * Scan the 'data' buffer for NULL-terminated strings.
     *
     * @return a collection of the strings found
     */
    SortedMap<Long, String> strings() {
        if (this.foundStrings == null) {
            this.foundStrings = new TreeMap<>();
            StringBuilder foundString = new StringBuilder();
            long addr = vma;
            for (int i = 0; i < data.length; i++)
                if (data[i] == 0) {
                    if (!foundString.toString().equals("")) {
                        foundStrings.put(addr, foundString.toString());
                        foundString = new StringBuilder();
                    }
                    // Compute address of next string.
                    addr = vma + i + 1;
                } else
                    foundString.append((char) data[i]);
        }
        return this.foundStrings;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("Section [vma = " + vma + ", file offset = " + offset + ", size = " + size + "]\n");
        strings().forEach((Long addr, String s) -> sb.append(addr).append(": String '").append(s).append("'\n"));
        return sb.toString();
    }

    public Set<Long> analyzeWords() {
        int wordSize;
        boolean littleEndian;

        switch (arch) {
        case X86:
            littleEndian = true;
            wordSize = 4;
            break;
        case X86_64:
            littleEndian = true;
            wordSize = 8;
            break;
        default:
            System.err.println("ERROR: analyzeWords() does not yet support " + arch);
            return words;
        }

        int countSize = size;
        if (size % wordSize != 0) {
            int size2 = (size / wordSize) * wordSize;
            System.err.println("Section size " + size + " not a multiple of " + wordSize + ", reading only first " + size2 + " bytes.");
            countSize = size2;
        }

        int[] factors = new int[wordSize];
        for (int i = 0; i < wordSize; i++)
            if (littleEndian)
                factors[i] = 1 << (i * wordSize);
            else
                factors[wordSize - i - 1] = 1 << (i * wordSize);

        for (int offset = 0; offset < countSize; offset += wordSize) {
            long value = 0;
            for (int i = 0; i < wordSize; i++)
                value += factors[i] * data[offset + i];
            words.add(value);
        }
        System.err.println("Words in data section: " + words.size());
        return words;
    }
}
