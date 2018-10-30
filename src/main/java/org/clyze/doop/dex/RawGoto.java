package org.clyze.doop.dex;

/**
 * This class represents a 'goto' instruction where the target is an
 * absolute instruction offset. During fact generation, this target
 * must be resolved to point to an instruction index.
 */
class RawGoto {
    final String insn;
    final int index;
    final int addrTo;
    public RawGoto(String insn, int index, int addrTo) {
        this.insn = insn;
        this.index = index;
        this.addrTo = addrTo;
    }
}
