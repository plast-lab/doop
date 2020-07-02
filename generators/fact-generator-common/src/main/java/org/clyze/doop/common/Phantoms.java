package org.clyze.doop.common;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class handles the existence of phantom references in analyzed code.
 */
public class Phantoms {
    private final boolean report;
    private final AtomicInteger counter = new AtomicInteger(0);

    /**
     * Create a new phantom references handler.
     * @param report   if true, messages appear in the output, otherwise only a number is shown
     */
    public Phantoms(boolean report) {
        this.report = report;
    }

    private void incrementPhantoms() {
        counter.incrementAndGet();
    }

    public void reportPhantom(String type, String id) {
        if (report)
            System.out.println(type + " " + id + " is phantom.");
        else
            incrementPhantoms();
    }

    public void reportPhantomSignature(String sig) {
        if (report)
            System.out.println("Method signature " + sig + " contains phantom types.");
        else
            incrementPhantoms();
    }

    public void reportPhantomSigType(String kind, String type, String sig) {
        if (report)
            System.out.println(kind + " " + type + " of " + sig + " is phantom.");
        else
            incrementPhantoms();
    }

    public void showPhantomInfo() {
        int phantomsFound = counter.get();
        if (phantomsFound > 0)
            System.out.println("Found " + phantomsFound + " phantom references. Rerun with '" + Parameters.OPT_REPORT_PHANTOMS + "' for more details.");
    }
}
