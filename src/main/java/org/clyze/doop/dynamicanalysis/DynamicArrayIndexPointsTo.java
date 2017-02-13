package org.clyze.doop.dynamicanalysis;

import org.clyze.doop.common.Database;
import org.clyze.doop.common.PredicateFile;

/**
 * Created by neville on 25/01/2017.
 */
public class DynamicArrayIndexPointsTo implements DynamicFact {
    private String baseHeap;

    private String heap;

    public DynamicArrayIndexPointsTo(String baseHeap, String heap) {
        this.baseHeap = baseHeap;
        this.heap = heap;
    }

    @Override
    public boolean equals(Object o) {

        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DynamicArrayIndexPointsTo that = (DynamicArrayIndexPointsTo) o;

        if (!baseHeap.equals(that.baseHeap)) return false;

        return heap.equals(that.heap);
    }

    @Override
    public int hashCode() {
        int result = baseHeap.hashCode();
        result = 31 * result + heap.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "DynamicArrayIndexPointsTo{" +
                "baseHeap='" + baseHeap + '\'' +
                ", heap='" + heap + '\'' +
                '}';
    }

    @Override
    public void write_fact(Database db) {
        db.add(PredicateFile.DYNAMIC_ARRAY_INDEX_POINTS_TO, baseHeap, heap);
    }
}
