package org.clyze.doop.dex;

import java.util.ArrayList;
import java.util.Collection;

/**
 * This is a helper class that supports the recognition of two-instruction patterns where
 * the first instruction uses an offset to point to the second instruction. This class is
 * used as follows:
 *
 * - When the first instruction is processed, call registerFirstInstructionData() to
 *   record the data for the second instruction to find.
 *
 * - When the second instruction is processed, its offset can be passed to
 *   getOriginalEntryForTarget() to retrieve the data of the original instruction.
 *
 * - When all method instructions are processed, checkEverythingConsumed() can be
 *   called to check that all patterns succeeded (no dangling "first" instructions left).
 *
 * @param <T>  the type of information recorded for the first instruction
 */
class PatternManager<T extends FirstInstructionEntry> {

    private final Collection<T> info = new ArrayList<>();

    public void registerFirstInstructionData(T entry) {
        info.add(entry);
    }

    public T getOriginalEntryForTarget(int currentOffset) {
        for (T entry : info) {
            if (entry.address == currentOffset) {
                info.remove(entry);
                return entry;
            }
        }
        throw new RuntimeException("Could not find original instruction for offset " + currentOffset);
    }

    /**
     * Check that all queued information has been consumed. This should
     * be called at the end of method processing.
     */
    public void checkEverythingConsumed() {
        if (!info.isEmpty())
            System.err.println("Error: residual FillArrayInfo of size " + info.size());
    }
}
