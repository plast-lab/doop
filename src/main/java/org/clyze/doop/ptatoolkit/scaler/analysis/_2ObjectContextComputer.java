package org.clyze.doop.ptatoolkit.scaler.analysis;

import org.clyze.doop.ptatoolkit.pta.basic.Method;
import org.clyze.doop.ptatoolkit.pta.basic.Obj;
import org.clyze.doop.ptatoolkit.pta.basic.Type;
import org.clyze.doop.ptatoolkit.scaler.doop.DoopPointsToAnalysis;
import org.clyze.doop.ptatoolkit.scaler.pta.PointsToAnalysis;

import java.util.*;

public class _2ObjectContextComputer extends ContextComputer {
    private Set<Method> visited = null;

    _2ObjectContextComputer(DoopPointsToAnalysis pta, ObjectAllocationGraph oag) {
        super(pta, oag);
    }

    @Override
    public String getAnalysisName() {
        return "2-object";
    }

    @Override
    protected long computeContextNumberOf(Method method) {
        visited = new HashSet<>();

        if (method.isInstance()) {
            if (pta.receiverObjectsOf(method).isEmpty()) {
                System.out.printf("2object - Empty receiver: %s\n", method.toString());
                return 1;
            }
        }
        Set<List<Obj>> totalPreds = getContexts(method);
        long contextNumber = totalPreds.size();

        return  contextNumber > 0? contextNumber: 1;
    }

    private Set<List<Obj>> getContexts(Method method) {
        if (methodToContextMap.containsKey(method)) {
            return methodToContextMap.get(method);
        }

        Set<List<Obj>> contexts = new HashSet<>();
        if (method.isImplicitReachable()) {
            contexts.add(Arrays.asList(super.pta.objFactory.get("immutable context"), super.pta.objFactory.get("immutable context")));
        }

        if (method.isInstance()) {
            visited.add(method);

            for (Obj recv : pta.receiverObjectsOf(method)) {
                Set<Obj> preds = oag.predsOf(recv);
                if (!preds.isEmpty()) {
                    for (Obj pred : preds) {
                        contexts.add(Arrays.asList(super.pta.objFactory.get(pred.toString()), super.pta.objFactory.get(recv.toString())));
                        contexts.add(Arrays.asList(super.pta.objFactory.get("immutable context"), super.pta.objFactory.get(recv.toString())));
                        contexts.add(Arrays.asList(super.pta.objFactory.get("immutable hcontext"), super.pta.objFactory.get(recv.toString())));

                    }
                } else {
                    // without allocator, back to 1-object
                    contexts.add(Collections.singletonList(super.pta.objFactory.get(recv.toString())));
                }
            }
            methodToContextMap.put(method, contexts);
        }
        else {
            for (Method caller : pta.callersOf(method)) {
                if (!visited.contains(caller)) {
                    visited.add(caller);
                    contexts.addAll(getContexts(caller));
                    contexts.add(Arrays.asList(super.pta.objFactory.get("immutable context"), super.pta.objFactory.get("immutable context")));
                }
            }
            methodToContextMap.put(method, contexts);
        }
        return contexts;
    }
}
