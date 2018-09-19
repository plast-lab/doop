package org.clyze.doop.dex;

class FirstInstructionEntry {
    final int currentOffset;
    final int offset;
    final int index;

    FirstInstructionEntry(int currentOffset, int offset, int index) {
        this.currentOffset = currentOffset;
        this.offset = offset;
        this.index = index;
    }
}
