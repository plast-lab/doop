package org.clyze.doop.common;

import java.util.HashMap;
import java.util.Map;

public class SessionCounter {

    /**
     * Keep the current count of temporary vars of a certain kind,
     * identified by base name.
     */
    private final Map<String, Integer> _tempVarMap = new HashMap<>();
    /**
     * Keeps the unique index of an instruction in the method. This cannot be
     * computed up front,because temporary variables (and assignments to them
     * from constants) will be inserted while the IR is being processed.
     */
    private final Map<Object, Integer> _units = new HashMap<>();
    /** Instruction index counter. */
    private int index = 0;

    public int nextNumber(String s) {
        Integer x = _tempVarMap.get(s);

        if (x == null)
            x = 0;

        _tempVarMap.put(s, x + 1);

        return x;
    }

    public int calcInstructionIndex(Object u) {
        // record the first unit number for this unit (to handle jumps)
        _units.putIfAbsent(u, index);
        return index++;
    }

    public int getInstructionIndex(Object u) {
        Integer result = _units.get(u);
        if (result == null)
            throw new RuntimeException("No unit number available for '" + u + "'");
        return result;
    }

}
