package org.clyze.doop.dex;

/**
 * This class represents a 'goto' instruction where the target is an
 * absolute instruction offset. During fact generation, this target
 * must be resolved to point to an instruction index.
 */
class RawGoto {
    final int codeAddr;
    final int index;
    public RawGoto(int codeAddr, int index) {
        this.codeAddr = codeAddr;
        this.index = index;
    }
}
