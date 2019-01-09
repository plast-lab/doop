package org.clyze.doop.ptatoolkit.scaler.analysis;

import org.clyze.doop.ptatoolkit.Global;
import org.clyze.doop.ptatoolkit.scaler.pta.PointsToAnalysis;
import org.clyze.doop.ptatoolkit.pta.basic.Method;
import org.clyze.doop.ptatoolkit.pta.basic.Obj;
import org.clyze.doop.ptatoolkit.pta.basic.Type;
import org.codehaus.groovy.util.HashCodeHelper;

import java.util.*;

public class _2TypeContextComputer extends ContextComputer {
    private Set<Method> visited = null;

    _2TypeContextComputer(PointsToAnalysis pta, ObjectAllocationGraph oag) {
        super(pta, oag);
    }

    @Override
    public String getAnalysisName() {
        return "2-type";
    }

    @Override
    protected int computeContextNumberOf(Method method) {
        visited = new HashSet<>();
        visited.add(method);

        if (method.isInstance()) {
            if (pta.receiverObjectsOf(method).isEmpty()) {
                if (Global.isDebug()) {
                    System.out.printf("Empty receiver: %s\n", method.toString());
                }
                return 1;
            }
        }
        return getContexts(method).size();

    }

    private Set<List<Type>> getContexts(Method method) {
        Set<List<Type>> contexts = new HashSet<>();
        if (method.isInstance()) {
            for (Obj recv : pta.receiverObjectsOf(method)) {
                Set<Obj> preds = oag.predsOf(recv);
                if (!preds.isEmpty()) {
                    for (Obj pred : preds) {
                        contexts.add(Arrays.asList(
                                pta.declaringAllocationTypeOf(pred),
                                pta.declaringAllocationTypeOf(recv)));
                    }
                } else {
                    // without allocator, back to 1-type
                    contexts.add(Collections.singletonList(
                            pta.declaringAllocationTypeOf(recv)));
                }
            }
            return contexts;
        }
        else {
            for (Method caller : pta.callersOf(method)) {
                if (!visited.contains(caller)) {
                    visited.add(caller);
                    contexts.addAll(getContexts(caller));
                }
            }
            return contexts;
        }
    }
}
