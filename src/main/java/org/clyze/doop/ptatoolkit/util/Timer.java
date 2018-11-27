package org.clyze.doop.ptatoolkit.util;

public class Timer {

    private String name;
    private long elapsedTime = 0;
    private long startTime;
    private boolean inCounting = false;

    public Timer(String name) {
        this.name = name;
    }

    public void start() {
        if (!inCounting) {
            inCounting = true;
            startTime = System.currentTimeMillis();
        }
    }

    public void stop() {
        if (inCounting) {
            elapsedTime += System.currentTimeMillis() - startTime;
            inCounting = false;
        }
    }

    public float inSecond() {
        return elapsedTime / 1000F;
    }

    public void clear() {
        elapsedTime = 0;
        inCounting = false;
    }

    @Override
    public String toString() {
        return String.format("(%s) elapsed time: %.2fs",
                name, inSecond());
    }
}
