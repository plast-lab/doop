package org.clyze.doop.ptatoolkit.util;

public class MutableLong {

    private long value;

    public MutableLong(long value) {
        this.value = value;
    }

    public void set(long value) {
        this.value = value;
    }

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
