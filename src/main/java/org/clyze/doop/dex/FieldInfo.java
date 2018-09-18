package org.clyze.doop.dex;

import org.clyze.doop.util.TypeUtils;
import org.jf.dexlib2.iface.reference.FieldReference;

import java.util.Collection;
import java.util.Map;

class FieldInfo {
    String definingClass;
    final String type;
    final String name;

    /**
     * Generate the field information for a field reference.
     * @param fieldRef     the field reference
     */
    FieldInfo(FieldReference fieldRef) {
        this.definingClass = TypeUtils.raiseTypeId(fieldRef.getDefiningClass());
        this.name = fieldRef.getName();
        this.type = TypeUtils.raiseTypeId(fieldRef.getType());
    }

    public String getFieldId() {
        return DexRepresentation.fieldId(definingClass, type, name);
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
        // System.out.println("Warning: cannot resolve field " + getFieldId());
        throw new FieldInfo.ResolveException();
    }

    private static boolean fieldInfoMatches(Collection<FieldInfo> fis, String name) {
        return (fis != null) && fis.stream().anyMatch(fi -> fi.name.equals(name));
    }

    public static class ResolveException extends Exception { }
}
