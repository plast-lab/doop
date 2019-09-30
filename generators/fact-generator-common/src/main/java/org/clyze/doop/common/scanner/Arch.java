package org.clyze.doop.common.scanner;

import java.io.IOException;

// The supported architectures.
enum Arch {
    X86, X86_64, AARCH64, ARMEABI, MIPS;

    static final Arch DEFAULT_ARCH = AARCH64;

    static Arch autodetect(String libFilePath) throws IOException {
        ProcessBuilder pb = new ProcessBuilder("file", libFilePath);
        Arch arch = null;
        for (String line : NativeScanner.runCommand(pb)) {
            if (line.contains("80386")) {
                arch = X86;
                break;
            } else if (line.contains("x86-64")) {
                arch = X86_64;
                break;
            } else if (line.contains("aarch64")) {
                arch = AARCH64;
                break;
            } else if (line.contains("ARM") || line.contains("EABI")) {
                arch = ARMEABI;
                break;
            } else if (line.contains("MIPS")) {
                arch = MIPS;
                break;
            }
        }
        if (arch != null)
            System.out.println("Detected architecture of " + libFilePath + " is " + arch);
        else {
            arch = DEFAULT_ARCH;
            System.out.println("Could not determine architecture of " + libFilePath + ", using default: " + arch);
        }
        return arch;
    }
}
