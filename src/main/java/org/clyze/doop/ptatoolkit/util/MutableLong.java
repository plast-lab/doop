package org.clyze.doop.ptatoolkit.util;

/**
 * Mutable wrapper for a long value.
 */
public class MutableLong {

    private long value;

    /**
     * Creates a mutable long.
     *
     * @param value the initial value
     */
    public MutableLong(long value) {
        this.value = value;
    }

    /**
     * Updates the wrapped value.
     *
     * @param value the new value
     */
    public void set(long value) {
        this.value = value;
    }

    /**
     * Returns the wrapped primitive value.
     *
     * @return the current long value
     */
    public long longValue() {
        return value;
    }

    /**
     * Increase the value by one and then return it.
     * @return the increased value.
     */
    public long increase() {
        return ++value;
    }
}
