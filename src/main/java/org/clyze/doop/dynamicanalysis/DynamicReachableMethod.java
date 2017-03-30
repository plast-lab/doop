package org.clyze.doop.dynamicanalysis;

import com.sun.tools.hat.internal.model.StackTrace;
import org.clyze.doop.common.Database;
import org.clyze.doop.common.PredicateFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Created by neville on 16/02/2017.
 */
public class DynamicReachableMethod implements DynamicFact {
    private final String representation;

    public static Collection<DynamicReachableMethod> fromStackTrace(StackTrace trace) {
        if (trace == null || trace.getFrames() == null)
            return new ArrayList<>();
        return Arrays.stream(trace.getFrames()).map(
                a -> new DynamicReachableMethod(DumpParsingUtil.fullyQualifiedMethodSignatureFromFrame(a))
        ).collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return "DynamicReachableMethod{" +
                "representation='" + representation + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DynamicReachableMethod that = (DynamicReachableMethod) o;

        return representation != null ? representation.equals(that.representation) : that.representation == null;
    }

    @Override
    public int hashCode() {
        return representation != null ? representation.hashCode() : 0;
    }

    public DynamicReachableMethod(String representation) {
        this.representation = representation;
    }

    @Override
    public void write_fact(Database db) {
        db.add(PredicateFile.DYNAMIC_REACHABLE_METHOD, representation);

    }
}
