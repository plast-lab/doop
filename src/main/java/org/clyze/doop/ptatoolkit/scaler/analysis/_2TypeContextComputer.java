package org.clyze.doop.ptatoolkit.scaler.analysis;

import org.clyze.doop.ptatoolkit.pta.basic.Method;
import org.clyze.doop.ptatoolkit.pta.basic.Obj;
import org.clyze.doop.ptatoolkit.pta.basic.Type;
import org.clyze.doop.ptatoolkit.scaler.doop.DoopPointsToAnalysis;

import java.util.*;

public class _2TypeContextComputer extends ContextComputer {
    private Set<Method> visited = null;


    _2TypeContextComputer(DoopPointsToAnalysis pta, ObjectAllocationGraph oag, ContextComputer worstCaseContextComputer) {
        super(pta, oag, worstCaseContextComputer);
    }

    @Override
    public String getAnalysisName() {
        return "2-type";
    }

    @Override
    protected long computeContextNumberOf(Method method) {
        visited = new HashSet<>();

        if (method.isInstance()) {
            if (pta.receiverObjectsOf(method).isEmpty()) {
                System.out.printf("2type - Empty receiver: %s\n", method.toString());
                return 1;
            }
        }
        else {
            return this.worstCaseContextComputer.contextNumberOf(method);
        }

        long contextNumber = getContexts(method).size();
        return  contextNumber > 0? contextNumber : 1;
    }

    private Set<List<Type>> getContexts(Method method) {
        Set<List<Type>> contexts = new HashSet<>();

        if (method.isImplicitReachable()) {
            contexts.add(Arrays.asList(super.pta.typeFactory.get("immutable context"), super.pta.typeFactory.get("immutable context")));
        }
        if (method.isInstance()) {
            visited.add(method);
            for (Obj recv : pta.receiverObjectsOf(method)) {
                Set<Obj> preds = oag.predsOf(recv);
                if (!preds.isEmpty()) {
                    for (Obj pred : preds) {
                        // Too strict, the allocating method of the predecessor method of the receiver may be analyzed with 2-object
                        // contexts.add(Arrays.asList(pta.declaringAllocationTypeOf(pred), pta.declaringAllocationTypeOf(recv)));
                        Type mockTypeForObject = pta.typeFactory.get("object as type " + pred);
                        contexts.add(Arrays.asList(mockTypeForObject, pta.declaringAllocationTypeOf(recv)));
                    }
                    Type immutableContext = pta.typeFactory.get("immutable context");
                    contexts.add(Arrays.asList(immutableContext, pta.declaringAllocationTypeOf(recv)));
                    Type immutableHContext = pta.typeFactory.get("immutable hcontext");
                    contexts.add(Arrays.asList(immutableHContext, pta.declaringAllocationTypeOf(recv)));
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
                    Type immutableContextComponent = pta.typeFactory.get("immutable context");
                    contexts.add(Arrays.asList(immutableContextComponent, immutableContextComponent));
                }
            }
            return contexts;
        }
    }
}
