package org.clyze.doop.common.scanner;

class XRef {
    public static final long NO_ADDRESS = -1;
    final String lib;
    final String function;
    final long codeAddr;
    XRef(String lib, String function, long codeAddr) {
        this.lib = lib;
        this.function = function;
        this.codeAddr = codeAddr;
    }
    public String toString() {
        return codeAddr + "@" + function + "@" + lib;
    }
}
