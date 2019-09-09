package org.clyze.doop.dex;

class FillArrayInfoEntry extends FirstInstructionEntry {
    final int reg;
    final NewArrayInfo newArrayInfo;

    FillArrayInfoEntry(int address, int reg, int index,
                       NewArrayInfo newArrayInfo) {
        super(address, index);
        this.reg = reg;
        this.newArrayInfo = newArrayInfo;
    }
}
