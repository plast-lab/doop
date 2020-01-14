package org.clyze.doop.common;

import java.util.Collection;
import java.util.Map;

public class FieldInfo {
    public String definingClass;
    public final String type;
    public final String name;

    @SuppressWarnings("WeakerAccess")
    public FieldInfo(String t, String n) {
        this.type = t;
        this.name = n;
    }

    public FieldInfo(String t, String n, String c) {
        this.type = t;
        this.name = n;
        this.definingClass = c;
    }

    public String getFieldId() {
        return JavaRepresentation.fieldId(definingClass, type, name);
    }

    /**
     * Finds where this field is really declared and updates the "declaring class" information.
     * Looks first in the current class, then in superclasses. This method needs whole-program
     * information and thus should be called after the whole program has been seen.
     *
     * @param definedClassFields    mapping from class names to their defined fields
     * @param superClass            mapping of superclass relations
     */
    public void resolveDeclaringClass(Map<String, Collection<FieldInfo>> definedClassFields,
                                      Map<String, String> superClass)
            throws FieldInfo.ResolveException {
        if (fieldInfoMatches(definedClassFields.get(definingClass), name))
            return;
        String current = definingClass;
        while (!"java.lang.Object".equals(current) && ((current = superClass.get(current)) != null))
            if (fieldInfoMatches(definedClassFields.get(current), name)) {
                this.definingClass = current;
                return;
            }
        // System.err.println("WARNING: cannot resolve field " + getFieldId());
        throw new FieldInfo.ResolveException();
    }

    private static boolean fieldInfoMatches(Collection<FieldInfo> fis, String name) {
        return (fis != null) && fis.stream().anyMatch(fi -> fi.name.equals(name));
    }

    static class ResolveException extends Exception { }
}
