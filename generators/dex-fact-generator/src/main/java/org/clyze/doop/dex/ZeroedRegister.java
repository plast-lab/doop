package org.clyze.doop.dex;

/**
 * A register thay was set to 0 by some instruction. Used to recognize patterns
 * (e.g., "set reg=0, new-array-with-size(reg)" = empty array creation).
 */
class ZeroedRegister {
    public final int index;
    public final int reg;

    ZeroedRegister(int index, int reg) {
        this.index = index;
        this.reg = reg;
    }
}
