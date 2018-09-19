package org.clyze.doop.dex;

class FillArrayInfoEntry extends FirstInstructionEntry {
    final int reg;
    final NewArrayInfo newArrayInfo;

    FillArrayInfoEntry(int currentOffset, int offset, int reg, int index,
                       NewArrayInfo newArrayInfo) {
        super(currentOffset, offset, index);
        this.reg = reg;
        this.newArrayInfo = newArrayInfo;
    }
}

