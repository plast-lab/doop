package org.clyze.doop.ptatoolkit.pta.basic;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * An instance of this class is a element in points-to analysis
 * that carries some attributes.
 */
public abstract class AttributeElement extends BasicElement {

    private Map<String, Object> attributes = new HashMap<>(4);

    public boolean hasAttribute(String name) {
        return attributes.containsKey(name);
    }

    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    public void setAttribute(String name, Object attr) {
        attributes.put(name, attr);
    }

    /**
     * For the case where name represents an attribute which
     * is a set of elements. This API directly adds the elem
     * into the attribute set (corresponding to name).
     * @param name
     * @param elem
     * @param <T>
     */
    @SuppressWarnings("unchecked")
    public <T> void addToAttributeSet(String name, T elem) {
        attributes.putIfAbsent(name, new HashSet<>());
        ((Set<T>) attributes.get(name)).add(elem);
    }

    /**
     *
     * @param name
     * @param <T>
     * @return the attribute set corresponding to name.
     * If the set does not exist, then return an empty set.
     */
    @SuppressWarnings("unchecked")
    public <T> Set<T> getAttributeSet(String name) {
        return (Set<T>) attributes.getOrDefault(name, Collections.emptySet());
    }
}
