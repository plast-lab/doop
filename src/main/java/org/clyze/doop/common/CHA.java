package org.clyze.doop.common;

import org.clyze.doop.common.Database;
import org.clyze.utils.TypeUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * This class keeps record of the type structure of the program (class hierarchy,
 * fields, methods). This is done to support the following:
 *
 * - Resolution of fields (e.g. "A.x" where "x" is a field of a superclass of A).
 *
 * - Detection of phantom types/fields/methods.
 *
 * A single instance of this class should be used for whole-program fact generation.
 */
public class CHA {
    // Type data.
    private final Collection<String> referencedTypes = new CopyOnWriteArrayList<>();
    private final Map<String, String> classInfo = new ConcurrentHashMap<>();
    // Method data.
    private final Collection<String> definedMethods = new CopyOnWriteArrayList<>();
    private final Collection<String> referencedMethods = new CopyOnWriteArrayList<>();
    // Field data.
    private final Map<String, Collection<FieldInfo>> definedClassFields = new ConcurrentHashMap<>();
    private final Collection<FieldOp> fieldOps = new CopyOnWriteArrayList<>();
    private final Collection<FieldInfo> referencedFields = new CopyOnWriteArrayList<>();
    private final List<String> phantomFields = new CopyOnWriteArrayList<>();

    public void registerReferencedType(String id) {
        if (!id.endsWith("[]") && !TypeUtils.isPrimitiveType(id))
            referencedTypes.add(id);
    }

    public void registerReferencedField(FieldInfo fi) {
        referencedFields.add(fi);
    }

    public void registerDefinedMethods(Collection<String> methods) {
        definedMethods.addAll(methods);
    }

    public void registerReferencedMethod(String id) {
        referencedMethods.add(id);
    }

    private static void writePhantoms(Collection<String> all, Collection<String> concrete,
                                      String desc, boolean print, Consumer<Set<String>> writeLambda) {
        // We use these set implementations as they are thread-safe
        // and keep results sorted (for presentation in the output).
        Set<String> set = new ConcurrentSkipListSet<>(all);
        set.removeAll(new ConcurrentSkipListSet<>(concrete));
        System.out.println("Number of phantom " + desc + ": " + set.size());
        if (print)
            writeLambda.accept(set);
    }

    public void queueFieldOps(Collection<FieldOp> ops) {
        fieldOps.addAll(ops);
    }

    /**
     * Write the queued field operations (resolving their field ids). This should run
     * at the end of whole-program fact generation, for field ids to be resolved.
     *
     * @param db    the database object to use
     */
    private void writeFieldOps(Database db) {
        System.out.println("Resolving and writing field operations...");

        Map<String, String> resolvedFields = new ConcurrentHashMap<>();
        for (FieldInfo fi : referencedFields) {
            String fieldIdBefore = fi.getFieldId();
            try {
                fi.resolveDeclaringClass(definedClassFields, classInfo);
                String fieldIdAfter = fi.getFieldId();
                if (!fieldIdBefore.equals(fieldIdAfter)) {
                    // System.out.println("Resolved: " + fieldIdBefore + " -> " + fieldIdAfter);
                    // Sanity check: the id has not already been resolved to a different id.
                    String existing = resolvedFields.get(fieldIdBefore);
                    if ((existing != null) && (!existing.equals(fieldIdAfter)))
                        System.err.println("WARNING: resolved " + fieldIdBefore + " to " + fieldIdAfter + ", overwriting existing: " + existing);
                    resolvedFields.put(fieldIdBefore, fieldIdAfter);
                }
            } catch (FieldInfo.ResolveException ex) {
                phantomFields.add(fieldIdBefore);
            }
        }
        System.out.println("Resolved " + resolvedFields.size() + " field references.");
        fieldOps.forEach(op -> op.writeToDb(db, resolvedFields));
    }

    /**
     * Assumes that fact generation of a whole program is about to finish so that:
     * (a) field operations can now be resolved and written, and (b) phantom
     * entities can be detected and reported.
     *
     * @param db              the database object to use
     * @param writer          the fact writer to use
     * @param reportPhantoms  if phantoms should be printed
     */
    public void conclude(Database db, JavaFactWriter writer, boolean reportPhantoms) {
        writeFieldOps(db);

        // Write phantom types.
        Set<String> definedTypes = classInfo.keySet();
        writePhantoms(referencedTypes, definedTypes, "types", reportPhantoms, writer::writePhantomTypes);
        // Write phantom methods.
        writePhantoms(referencedMethods, definedMethods, "methods", reportPhantoms, writer::writePhantomMethods);
        // Report phantom fields.
        System.out.println("Number of phantom fields: " + phantomFields.size());
        if (reportPhantoms)
            phantomFields.forEach((String s) -> System.out.println("Phantom field: " + s));
    }

    public void registerSuperClass(String sub, String sup) {
        String sup0 = classInfo.get(sub);
        if (sup0 == null)
            classInfo.put(sub, sup);
        else if (!sup0.equals(sup))
            throw new RuntimeException("Cannot set superclass of " + sub + " to " + sup + ", it already is " + sup0);
    }

    public void registerDefinedClassFields(String className, Collection<FieldInfo> fis) {
        Collection<FieldInfo> fields = definedClassFields.get(className);
        if (fields == null) {
            fields = new CopyOnWriteArrayList<>();
            definedClassFields.put(className, fields);
        }
        fields.addAll(fis);
    }
}
