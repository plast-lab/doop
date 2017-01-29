package org.clyze.doop.dynamicanalysis;

import org.clyze.doop.common.Database;
import org.clyze.doop.common.PredicateFile;

/**
 * Created by neville on 25/01/2017.
 */
public class InstanceFieldPointsTo implements DynamicFact{
    private String baseHeap;
    private String field;
    private String heap;

    public InstanceFieldPointsTo(String baseHeap, String field, String heap) {
        this.baseHeap = baseHeap;
        this.field = field;
        this.heap = heap;
    }

    @Override
    public boolean equals(Object o) {

        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        InstanceFieldPointsTo that = (InstanceFieldPointsTo) o;

        if (!baseHeap.equals(that.baseHeap)) return false;
        if (!field.equals(that.field)) return false;
        return heap.equals(that.heap);
    }

    @Override
    public int hashCode() {
        int result = baseHeap.hashCode();
        result = 31 * result + field.hashCode();
        result = 31 * result + heap.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "InstanceFieldPointsTo{" +
                "baseHeap='" + baseHeap + '\'' +
                ", field='" + field + '\'' +
                ", heap='" + heap + '\'' +
                '}';
    }

    @Override
    public void write_fact(Database db) {
        db.add(PredicateFile.DYNAMIC_INSTANCE_FIELD_POINTS_TO, baseHeap, field, heap);
    }
}
