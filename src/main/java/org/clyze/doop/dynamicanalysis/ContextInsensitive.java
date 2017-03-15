package org.clyze.doop.dynamicanalysis;

import org.clyze.doop.common.Database;

/**
 * Created by neville on 15/03/2017.
 */
public class ContextInsensitive implements Context {
    @Override
    public void write_fact(Database db) {

    }


    @Override
    public String getRepresentation() {
        return "<ImmutableCtx>";
    }

    public int hashCode() {
        return 12312;
    }

    public boolean equals(Object other) {
        return other.getClass().equals(this.getClass());
    }
}
