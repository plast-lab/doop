package org.clyze.doop.dynamicanalysis;

import org.clyze.doop.common.Database;
import org.clyze.doop.common.PredicateFile;

/**
 * Created by neville on 15/03/2017.
 */
public class DoubleContext<T extends ComposableContext> implements Context {

    private final T ctx1;
    private final T ctx2;
    private final String representation;

    public DoubleContext(T ctx1, T ctx2) {
        this.ctx1 = ctx1;
        this.ctx2 = ctx2;
        this.representation = ctx1.getRepresentation() + "..." + ctx2.getRepresentation();
    }

    @Override
    public final void write_fact(Database db) {
        String[] args = DEFAULT_CTX_ARGS.clone();

        for (int i = 0, j = ctx1.getStartIndex();
             i < ctx1.getComponents().length;
             i++, i++)
            args[j] = ctx1.getComponents()[i];


        for (int i = 0, j = ctx2.getStartIndex() + ctx2.getComponents().length;
             i < ctx2.getComponents().length;
             i++, i++)
            args[j] = ctx2.getComponents()[i];
        db.add(PredicateFile.DYNAMIC_CONTEXT, getRepresentation(), args);
    }

    @Override
    public String getRepresentation() {
        return representation;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DoubleContext<?> doubleContext = (DoubleContext<?>) o;

        if (ctx1 != null ? !ctx1.equals(doubleContext.ctx1) : doubleContext.ctx1 != null) return false;
        return ctx2 != null ? ctx2.equals(doubleContext.ctx2) : doubleContext.ctx2 == null;
    }

    @Override
    public int hashCode() {
        int result = ctx1 != null ? ctx1.hashCode() : 0;
        result = 31 * result + (ctx2 != null ? ctx2.hashCode() : 0);
        return result;
    }
}
