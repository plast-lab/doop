package org.clyze.doop.common;

import java.util.Map;

public class FieldOp {
    private final PredicateFile target;
    private final String insn;
    private final String strIndex;
    private final String localA;
    private final String localB;
    private final FieldInfo fieldInfo;
    private final String methId;

    /**
     * A field operation (read/write instance/static field).
     * @param target      the predicate file to use for writing
     * @param insn        the instruction id
     * @param strIndex    the instruction index
     * @param localA      the first local for instance field operations
     *                    (or the only local for static field operations)
     * @param localB      the second local for instance field operations
     *                    (or null for static field operations)
     * @param fieldInfo   the field id
     * @param methId      the enclosing method id
     */
    public FieldOp(PredicateFile target, String insn, String strIndex,
                   String localA, String localB, FieldInfo fieldInfo,
                   String methId) {
        this.target = target;
        this.insn = insn;
        this.strIndex = strIndex;
        this.localA = localA;
        this.localB = localB;
        this.fieldInfo = fieldInfo;
        this.methId = methId;
    }

    void writeToDb(Database db, Map<String, String> resolvedFields) {
        String fieldId = fieldInfo.getFieldId();
        String resolvedFieldId = resolvedFields.getOrDefault(fieldId, fieldId);
        if (localB != null)
            db.add(target, insn, strIndex, localA, localB, resolvedFieldId, methId);
        else
            db.add(target, insn, strIndex, localA, resolvedFieldId, methId);
    }
}
