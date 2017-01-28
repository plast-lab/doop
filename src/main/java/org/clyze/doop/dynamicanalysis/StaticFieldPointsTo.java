package org.clyze.doop.dynamicanalysis;

/**
 * Created by neville on 25/01/2017.
 */
public class StaticFieldPointsTo implements DynamicFact{
    private String sig;
    private String heap;

    public StaticFieldPointsTo(String sig,  String heap) {
        this.sig = sig;
        this.heap = heap;
    }

    @Override
    public boolean equals(Object o) {

        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        StaticFieldPointsTo that = (StaticFieldPointsTo) o;

        if (!sig.equals(that.sig)) return false;
        return heap.equals(that.heap);
    }

    @Override
    public int hashCode() {
        int result = sig.hashCode();
        result = 31 * result + heap.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "StaticFieldPointsTo{" +
                "sig='" + sig + '\'' +
                ", heap='" + heap + '\'' +
                '}';
    }
}
