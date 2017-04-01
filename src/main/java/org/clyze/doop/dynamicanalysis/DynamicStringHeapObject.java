package org.clyze.doop.dynamicanalysis;

import org.clyze.doop.common.Database;
import org.clyze.doop.common.FactEncoders;

import static org.clyze.doop.common.PredicateFile.*;

/**
 * Created by neville on 15/02/2017.
 */
public class DynamicStringHeapObject implements DynamicHeapObject {
    private final String representation;

    public DynamicStringHeapObject(String stringValue) {
        this.representation = FactEncoders.encodeStringConstant(stringValue);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DynamicStringHeapObject that = (DynamicStringHeapObject) o;

        return getRepresentation().equals(that.getRepresentation());
    }

    @Override
    public int hashCode() {
        return getRepresentation().hashCode();
    }

    public String getRepresentation() {
        return representation;

    }

    @Override
    public String getContextRepresentation() {
        return null;
    }

    @Override
    public String getHeapRepresentation() {
        return null;
    }

    @Override
    public boolean isProbablyUnmatched() {
        return false;
    }

    @Override
    public void write_fact(Database db) {
        // To think about, keeping track of all raw strings is infeasable for now.
        //db.add(STRING_RAW, representation, representation);
        //db.add(STRING_CONST, representation);
        db.add(DYNAMIC_STRING_HEAP_OBJECT, representation);
    }
}
