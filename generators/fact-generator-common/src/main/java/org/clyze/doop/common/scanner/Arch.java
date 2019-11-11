package org.clyze.doop.common.scanner;

import java.io.IOException;

// The supported architectures.
enum Arch {
    X86, X86_64, AARCH64, ARMEABI, MIPS;

    static final Arch DEFAULT_ARCH = AARCH64;
}
