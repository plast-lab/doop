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

    /**
     * Checks if the element has an attribute with the given name.
     * @param name the name of the attribute to check for
     * @return true if the element has an attribute with the given name, false otherwise
     */
    public boolean hasAttribute(String name) {
        return attributes.containsKey(name);
    }

    /**
     * Retrieves the attribute associated with the given name. If the attribute does not exist, it returns null.
     * @param name the name of the attribute to retrieve
     * @return the attribute associated with the given name, or null if the attribute does not exist
     */
    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    public void setAttribute(String name, Object attr) {
        attributes.put(name, attr);
    }

    /** For the case where name represents an attribute which
     * is a set of elements. This API directly adds the elem
     * into the attribute set (corresponding to name).
     * @param name the name of the attribute set to which the element should be added
     * @param elem the element to be added to the attribute set corresponding to the given name
     * @param <T> the type of the element being added to the attribute set
     */
    @SuppressWarnings("unchecked")
    public <T> void addToAttributeSet(String name, T elem) {
        attributes.putIfAbsent(name, new HashSet<>());
        ((Set<T>) attributes.get(name)).add(elem);
    }

    /** Retrieves the attribute set corresponding to the given name. If the set does not exist, it returns an empty set.
     * @param name the name of the attribute set to retrieve 
     * @param <T> the type of the elements in the attribute set
     * @return the attribute set corresponding to name.
     * If the set does not exist, then return an empty set.
     */
    @SuppressWarnings("unchecked")
    public <T> Set<T> getAttributeSet(String name) {
        return (Set<T>) attributes.getOrDefault(name, Collections.emptySet());
    }
}
