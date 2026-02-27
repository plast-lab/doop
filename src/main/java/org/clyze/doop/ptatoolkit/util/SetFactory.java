package org.clyze.doop.ptatoolkit.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * This class helps eliminate redundant equal sets.
 *
 * @param <T> the element type of managed sets
 */
public class SetFactory<T> {

    private final Map<Set<T>, Set<T>> sets = new HashMap<>();

    /**
     * Returns a canonical representative for an equal set.
     * If this is the first equal set encountered, the provided set is retained.
     *
     * @param set the set to canonicalize
     * @return a previously seen equal set or {@code set} itself
     */
    public Set<T> get(Set<T> set) {
        if (!sets.containsKey(set)) {
            sets.put(set, set);
        }
        return sets.get(set);
    }

}
