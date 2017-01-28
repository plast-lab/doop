package org.clyze.doop.dynamicanalysis;

/**
 * Created by neville on 25/01/2017.
 */
public class ArrayIndexPointsTo implements DynamicFact {
    private String baseHeap;

    private String heap;

    public ArrayIndexPointsTo(String baseHeap, String heap) {
        this.baseHeap = baseHeap;
        this.heap = heap;
    }

    @Override
    public boolean equals(Object o) {

        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ArrayIndexPointsTo that = (ArrayIndexPointsTo) o;

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
        return "ArrayIndexPointsTo{" +
                "baseHeap='" + baseHeap + '\'' +
                ", heap='" + heap + '\'' +
                '}';
    }
}
