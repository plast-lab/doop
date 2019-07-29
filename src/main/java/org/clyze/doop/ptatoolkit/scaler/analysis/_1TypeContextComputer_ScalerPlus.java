package org.clyze.doop.ptatoolkit.scaler.analysis;

import org.clyze.doop.ptatoolkit.pta.basic.Method;
import org.clyze.doop.ptatoolkit.pta.basic.Obj;
import org.clyze.doop.ptatoolkit.scaler.doop.DoopPointsToAnalysis;

import java.util.HashSet;
import java.util.Set;

public class _1TypeContextComputer_ScalerPlus extends ContextComputer {
    private Set<Method> visited = new HashSet<>();

    _1TypeContextComputer_ScalerPlus(DoopPointsToAnalysis pta, ObjectAllocationGraph oag, ContextComputer worstCaseContextComputer) {
	    super(pta, oag, worstCaseContextComputer);
    }

    @Override
    public String getAnalysisName() {
        return "1-type";
    }

    @Override
    protected long computeContextNumberOf(Method method) {
        visited = new HashSet<>();
        Set<Obj> totalReceiverObjects = getReceiverObjects(method);
        if (method.isInstance()) {
            if (totalReceiverObjects.isEmpty()) {
                System.out.printf("1type- Empty receiver: %s\n", method.toString());
                return 1;
            }
        }
        else {
            return worstCaseContextComputer.contextNumberOf(method);
        }
        long contextNumber = totalReceiverObjects.stream()
                .map(pta::declaringAllocationTypeOf)
                .distinct()
                .count();

        return contextNumber > 0? contextNumber: 1;
    }

    private Set<Obj> getReceiverObjects(Method method) {
        Set<Obj> totalReceiverObjects = new HashSet<>();
        if (method.isInstance()) {
            visited.add(method);
            totalReceiverObjects.addAll(pta.receiverObjectsOf(method));
        }
        else {
            for (Method caller : pta.callersOf(method)) {
                if (!visited.contains(caller)) {
                    visited.add(caller);
                    totalReceiverObjects.addAll(getReceiverObjects((caller)));
                }
            }
        }
        return totalReceiverObjects;
    }
}
