package org.clyze.doop.dex;

class MoveExceptionInfo {
    final int reg;
    final int address;
    final int index;
    MoveExceptionInfo(int reg, int address, int index) {
        this.reg = reg;
        this.address = address;
        this.index = index;
    }
}
