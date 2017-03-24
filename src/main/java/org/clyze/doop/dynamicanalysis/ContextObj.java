package org.clyze.doop.dynamicanalysis;

import org.clyze.doop.common.Database;

/**
 * Created by neville on 15/03/2017.
 */
public class ContextObj implements ComposableContext {

    private final String lineNumber;
    private final String inMethod;
    private final String type;
    private final String representation;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ContextObj that = (ContextObj) o;

        return getRepresentation() != null ? getRepresentation().equals(that.getRepresentation()) : that.getRepresentation() == null;
    }

    @Override
    public int hashCode() {
        return getRepresentation() != null ? getRepresentation().hashCode() : 0;
    }

    public ContextObj(String lineNumber, String inMethod, String type) {
        this.lineNumber = lineNumber;
        this.inMethod = inMethod;
        this.type = type;
        this.representation = DynamicNormalHeapObject.getAllocationRepresentation(lineNumber, inMethod, type);
    }
    @Override
    public void write_fact(Database db) {
        throw new RuntimeException();
    }

    public int getStartIndex() { return 2; }

    public String[] getComponents() {
        return new String[] { representation };
    }

    public String getRepresentation() {
        return representation;
    }


}
