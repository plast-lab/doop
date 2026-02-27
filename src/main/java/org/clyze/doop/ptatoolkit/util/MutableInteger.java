package org.clyze.doop.ptatoolkit.util;

/**
 * Mutable wrapper for an integer value.
 */
public class MutableInteger {

    private int value;

    /**
     * Creates a mutable integer.
     *
     * @param value the initial value
     */
    public MutableInteger(int value) {
        this.value = value;
    }

    /**
     * Updates the wrapped value.
     *
     * @param value the new value
     */
    public void set(int value) {
        this.value = value;
    }

    /**
     * Returns the wrapped primitive value.
     *
     * @return the current integer value
     */
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
