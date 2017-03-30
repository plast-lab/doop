package org.clyze.doop.dynamicanalysis;

import org.clyze.doop.common.Database;
import org.clyze.doop.common.PredicateFile;

/**
 * Created by neville on 25/01/2017.
 */
public class DynamicInstanceFieldPointsTo implements DynamicFact{
    private final String baseHeap;
    private final String fieldName;
    private final String fieldDeclaringClass;
    private final String heap;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DynamicInstanceFieldPointsTo that = (DynamicInstanceFieldPointsTo) o;

        if (baseHeap != null ? !baseHeap.equals(that.baseHeap) : that.baseHeap != null) return false;
        if (fieldName != null ? !fieldName.equals(that.fieldName) : that.fieldName != null) return false;
        if (fieldDeclaringClass != null ? !fieldDeclaringClass.equals(that.fieldDeclaringClass) : that.fieldDeclaringClass != null)
            return false;
        return heap != null ? heap.equals(that.heap) : that.heap == null;
    }

    @Override
    public int hashCode() {
        int result = baseHeap != null ? baseHeap.hashCode() : 0;
        result = 31 * result + (fieldName != null ? fieldName.hashCode() : 0);
        result = 31 * result + (fieldDeclaringClass != null ? fieldDeclaringClass.hashCode() : 0);
        result = 31 * result + (heap != null ? heap.hashCode() : 0);
        return result;
    }

    public DynamicInstanceFieldPointsTo(String baseHeap, String fieldName, String fieldDeclaringClass, String heap) {
        this.baseHeap = baseHeap;
        this.fieldName = fieldName;
        this.fieldDeclaringClass = fieldDeclaringClass;
        this.heap = heap;
    }

    @Override
    public String toString() {
        return "DynamicInstanceFieldPointsTo{" +
                "baseHeap='" + baseHeap + '\'' +
                ", fieldName='" + fieldName + '\'' +
                ", fieldDeclaringClass='" + fieldDeclaringClass + '\'' +
                ", heap='" + heap + '\'' +
                '}';
    }

    @Override
    public void write_fact(Database db) {
        db.add(PredicateFile.DYNAMIC_INSTANCE_FIELD_POINTS_TO, baseHeap, fieldName, fieldDeclaringClass, heap);
    }
}
