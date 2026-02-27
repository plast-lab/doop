package org.clyze.doop.ptatoolkit.util;

/**
 * Utility for measuring elapsed wall-clock time across one or more intervals.
 */
public class Timer {

    private String name;
    private long elapsedTime = 0;
    private long startTime;
    private boolean inCounting = false;

    /**
     * Creates a timer with a human-readable name.
     *
     * @param name the timer name
     */
    public Timer(String name) {
        this.name = name;
    }

    /**
     * Starts timing if the timer is currently stopped.
     */
    public void start() {
        if (!inCounting) {
            inCounting = true;
            startTime = System.currentTimeMillis();
        }
    }

    /**
     * Stops timing and accumulates elapsed time if currently running.
     */
    public void stop() {
        if (inCounting) {
            elapsedTime += System.currentTimeMillis() - startTime;
            inCounting = false;
        }
    }

    /**
     * Returns the elapsed time in seconds.
     *
     * @return the elapsed time in seconds
     */
    public float inSecond() {
        return elapsedTime / 1000F;
    }

    /**
     * Resets elapsed time and marks the timer as stopped.
     */
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
