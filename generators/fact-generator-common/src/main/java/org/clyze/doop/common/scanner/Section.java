package org.clyze.doop.common.scanner;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.*;

// A representation of a section in a binary.
class Section {
    private final Arch arch;
    private final int offset;
    private final int size;
    private final byte[] data;
    private SortedMap<Long, String> foundStrings;
    private final Set<Long> words;

    private Section(Arch arch, int offset, int size, byte[] data) {
        this.arch = arch;
        this.offset = offset;
        this.size = size;
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
                                      String sectionName, List<String> lines)
        throws IOException {

        int sizeIdx = -1;
        int offsetIdx = -1;
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
            if (line.contains(sectionName + " ")) {
                if ((sizeIdx == -1) || (offsetIdx == -1)) {
                    System.err.println("Error, cannot find section " + sectionName + " from output:");
                    for (String l : lines)
                        System.out.println(l);
                    return null;
                } else {
                    int sizeEndIdx = line.indexOf(' ', sizeIdx);
                    int offsetEndIdx = line.indexOf(' ', offsetIdx);
                    int size = (int)Long.parseLong(line.substring(sizeIdx, sizeEndIdx), 16);
                    int offset = (int)Long.parseLong(line.substring(offsetIdx, offsetEndIdx), 16);
                    System.out.println(sectionName + " section: offset = " + offset + ", size = " + size);

                    // Read section from the library.
                    RandomAccessFile raf = new RandomAccessFile(lib, "r");
                    raf.seek(offset);
                    byte[] bytes = new byte[size];
                    raf.readFully(bytes);

                    System.out.println("Section fully read: " + sectionName);
                    return new Section(arch, offset, size, bytes);
                }
            }
        }
        System.out.println("Library " + lib + " does not contain a " + sectionName + " section.");
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
