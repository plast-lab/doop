package org.clyze.doop.dynamicanalysis;

import org.clyze.doop.common.Database;

/**
 * Created by neville on 15/03/2017.
 */
public class Context2<T extends Context> implements Context {

    private final T ctx1;
    private final T ctx2;
    private final String representation;

    public Context2(T ctx1, T ctx2) {
        this.ctx1 = ctx1;
        this.ctx2 = ctx2;
        this.representation = ctx1.getRepresentation() + "..." + ctx2.getRepresentation();
    }

    @Override
    public void write_fact(Database db) {

    }

    @Override
    public String getRepresentation() {
        return representation;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Context2<?> context2 = (Context2<?>) o;

        if (ctx1 != null ? !ctx1.equals(context2.ctx1) : context2.ctx1 != null) return false;
        return ctx2 != null ? ctx2.equals(context2.ctx2) : context2.ctx2 == null;
    }

    @Override
    public int hashCode() {
        int result = ctx1 != null ? ctx1.hashCode() : 0;
        result = 31 * result + (ctx2 != null ? ctx2.hashCode() : 0);
        return result;
    }
}
