package org.clyze.doop.dynamicanalysis;

import org.clyze.doop.common.Database;
import org.clyze.doop.common.PredicateFile;

/**
 * Created by neville on 08/02/2017.
 */
public class DynamicHeapAllocation implements DynamicFact {


    private String representation;
    private String lineNumber;
    private String inMethod;
    private String type;

    public DynamicHeapAllocation(String representation, String lineNumber, String inMethod, String type) {
        this.representation = representation;
        this.lineNumber = lineNumber;
        this.inMethod = inMethod;
        this.type = type;
    }



    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DynamicHeapAllocation that = (DynamicHeapAllocation) o;

        if (representation != null ? !representation.equals(that.representation) : that.representation != null)
            return false;
        if (getLineNumber() != null ? !getLineNumber().equals(that.getLineNumber()) : that.getLineNumber() != null)
            return false;
        if (getInMethod() != null ? !getInMethod().equals(that.getInMethod()) : that.getInMethod() != null)
            return false;
        return getType() != null ? getType().equals(that.getType()) : that.getType() == null;
    }

    @Override
    public int hashCode() {
        int result = representation != null ? representation.hashCode() : 0;
        result = 31 * result + (getLineNumber() != null ? getLineNumber().hashCode() : 0);
        result = 31 * result + (getInMethod() != null ? getInMethod().hashCode() : 0);
        result = 31 * result + (getType() != null ? getType().hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "DynamicHeapAllocation{" +
                "lineNumber='" + lineNumber + '\'' +
                ", inMethod='" + inMethod + '\'' +
                ", type='" + type + '\'' +
                '}';
    }

    @Override
    public void write_fact(Database db) {
        db.add(PredicateFile.DYNAMIC_HEAP_ALLOCATION, lineNumber, inMethod, type, representation);
    }

    public String getRepresentation() {
        return representation;
    }

    public void setRepresentation(String representation) {
        this.representation = representation;
    }

    public String getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(String lineNumber) {
        this.lineNumber = lineNumber;
    }

    public String getInMethod() {
        return inMethod;
    }

    public void setInMethod(String inMethod) {
        this.inMethod = inMethod;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
