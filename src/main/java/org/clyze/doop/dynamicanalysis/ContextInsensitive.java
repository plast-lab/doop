package org.clyze.doop.dynamicanalysis;

import org.clyze.doop.common.Database;

/**
 * Created by neville on 15/03/2017.
 */
public class ContextInsensitive implements Context {
    public static final String IMMUTABLE_DCTX = "<Immutable dctx>";
    private static Context instance = null;
    public static Context get() {
        if (instance == null)
            instance = new ContextInsensitive();
        return instance;
    }

    @Override
    public void write_fact(Database db) {
        // do not write to db!
    }


    @Override
    public String getRepresentation() {
        return IMMUTABLE_DCTX;
    }

    public int hashCode() {
        return 12312;
    }

    public boolean equals(Object other) {
        return other.getClass().equals(this.getClass());
    }
}
