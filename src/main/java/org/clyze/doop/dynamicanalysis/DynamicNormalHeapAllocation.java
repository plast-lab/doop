package org.clyze.doop.dynamicanalysis;

import org.clyze.doop.common.Database;
import org.clyze.doop.common.PredicateFile;

import java.util.Arrays;

/**
 * Created by neville on 08/02/2017.
 */
public class DynamicNormalHeapAllocation implements DynamicHeapAllocation {


    private String representation;
    private String[] lineNumber;
    private String[] inMethod;
    private String type;

    private transient boolean probablyUnmatched = false;

    public DynamicNormalHeapAllocation(String[] lineNumber, String[] inMethod, String type) {
        String[] zipped = new String[lineNumber.length];
        for (int i = 0; i<lineNumber.length; i++) {
            zipped[i] = inMethod[i] + ":" + lineNumber[i];
        }
        representation = String.join("...", zipped) + "/new " + type;
        this.lineNumber = lineNumber;
        this.inMethod = inMethod;
        this.type = type;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DynamicNormalHeapAllocation that = (DynamicNormalHeapAllocation) o;

        if (getRepresentation() != null ? !getRepresentation().equals(that.getRepresentation()) : that.getRepresentation() != null)
            return false;
        // Probably incorrect - comparing Object[] arrays with Arrays.equals
        if (!Arrays.equals(lineNumber, that.lineNumber)) return false;
        // Probably incorrect - comparing Object[] arrays with Arrays.equals
        if (!Arrays.equals(inMethod, that.inMethod)) return false;
        return getType() != null ? getType().equals(that.getType()) : that.getType() == null;
    }

    @Override
    public int hashCode() {
        int result = getRepresentation() != null ? getRepresentation().hashCode() : 0;
        result = 31 * result + Arrays.hashCode(lineNumber);
        result = 31 * result + Arrays.hashCode(inMethod);
        result = 31 * result + (getType() != null ? getType().hashCode() : 0);
        return result;
    }

    @Override
    public void write_fact(Database db) {
        for (int i = 0; i<lineNumber.length; i++)
            db.add(PredicateFile.DYNAMIC_NORMAL_HEAP_ALLOCATION, lineNumber[i], inMethod[i], ""+i, type, representation);
    }

    @Override
    public String getRepresentation() {
        return representation;
    }

    public String getType() {
        return type;
    }

    @Override
    public boolean isProbablyUnmatched() {
        return probablyUnmatched;
    }

    public void setProbablyUnmatched(boolean probablyUnmatched) {
        this.probablyUnmatched = probablyUnmatched;
    }
}
