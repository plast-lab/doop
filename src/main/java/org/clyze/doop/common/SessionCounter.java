package org.clyze.doop.common;

import java.util.HashMap;
import java.util.Map;

public class SessionCounter {

    /**
     * Keep the current count of temporary vars of a certain kind,
     * identified by base name.
     */
    private Map<String, Integer> _tempVarMap = new HashMap<>();

    public int nextNumber(String s) {
        Integer x = _tempVarMap.get(s);

        if(x == null)
            x = 0;

        _tempVarMap.put(s, x + 1);

        return x;
    }
}
