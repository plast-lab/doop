package org.clyze.doop.ptatoolkit.util;

public class MutableInteger {

    private int value;

    public MutableInteger(int value) {
        this.value = value;
    }

    public void set(int value) {
        this.value = value;
    }

    public int intValue() {
        return value;
    }

    /**
     * Increase the value by one and then return it.
     * @return the increased value.
     */
    public int increase() {
        return ++value;
    }
}
