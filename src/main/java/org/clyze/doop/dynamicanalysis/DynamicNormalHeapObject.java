package org.clyze.doop.dynamicanalysis;

import org.clyze.doop.common.Database;
import org.clyze.doop.common.PredicateFile;

/**
 * Created by neville on 08/02/2017.
 */
public class DynamicNormalHeapObject implements DynamicHeapObject {


    private String representation;
    private String lineNumber;
    private String inMethod;
    private String type;
    private String contextRepresentation;

    private transient boolean probablyUnmatched = false;
    private String heapRepresentation;

    public DynamicNormalHeapObject(String lineNumber, String inMethod, String type, String contextRepresentation) {
        this.contextRepresentation = contextRepresentation;
        this.lineNumber = lineNumber;
        this.inMethod = inMethod;
        this.type = type;
        heapRepresentation = getAllocationRepresentation(lineNumber, inMethod, type);
        representation = heapRepresentation + "@" + contextRepresentation;
    }

    public static String getAllocationRepresentation(String lineNumber, String inMethod, String type) {
        return inMethod + ":" + lineNumber + "/new " + type;
    }


    @Override
    public void write_fact(Database db) {
        db.add(PredicateFile.DYNAMIC_NORMAL_HEAP_ALLOCATION, lineNumber, inMethod, type, heapRepresentation);
        db.add(PredicateFile.DYNAMIC_NORMAL_HEAP_OBJECT, heapRepresentation, contextRepresentation, representation);
    }

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DynamicNormalHeapObject that = (DynamicNormalHeapObject) o;

        return getRepresentation() != null ? getRepresentation().equals(that.getRepresentation()) : that.getRepresentation() == null;
    }

    @Override
    public int hashCode() {
        return getRepresentation() != null ? getRepresentation().hashCode() : 0;
    }
}
