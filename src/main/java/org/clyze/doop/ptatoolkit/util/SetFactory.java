package org.clyze.doop.ptatoolkit.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * This class helps eliminate redundant equal sets.
 * @param <T>
 */
public class SetFactory<T> {

    private final Map<Set<T>, Set<T>> sets = new HashMap<>();

    public Set<T> get(Set<T> set) {
        if (!sets.containsKey(set)) {
            sets.put(set, set);
        }
        return sets.get(set);
    }

}
