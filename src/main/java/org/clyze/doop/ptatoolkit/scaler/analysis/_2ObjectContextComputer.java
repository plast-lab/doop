package org.clyze.doop.ptatoolkit.scaler.analysis;

import org.clyze.doop.ptatoolkit.pta.basic.Method;
import org.clyze.doop.ptatoolkit.pta.basic.Obj;
import org.clyze.doop.ptatoolkit.scaler.pta.PointsToAnalysis;

import java.util.HashSet;
import java.util.Set;

public class _2ObjectContextComputer extends ContextComputer {
    private Set<Method> visited = null;

    _2ObjectContextComputer(PointsToAnalysis pta, ObjectAllocationGraph oag) {
        super(pta, oag);
    }

    @Override
    public String getAnalysisName() {
        return "2-object";
    }

    @Override
    protected long computeContextNumberOf(Method method) {
        visited = new HashSet<>();
        visited.add(method);

        if (method.isInstance()) {
            if (pta.receiverObjectsOf(method).isEmpty()) {
                System.out.printf("2object - Empty receiver: %s\n", method.toString());
                return 1;
            }
        }

        Set<Obj> totalPreds = getPreds(method);
        long contextNumber = totalPreds.size();

        return  contextNumber > 0? contextNumber: 1;
    }

    private Set<Obj> getPreds(Method method) {
        Set<Obj> totalPreds = new HashSet<>();

        if (method.isInstance()) {
            for (Obj recv : pta.receiverObjectsOf(method)) {
                Set<Obj> preds = oag.predsOf(recv);
                if (!preds.isEmpty()) {
                    totalPreds.addAll(preds);
                } else {
                    // without allocator, back to 1-object
                    totalPreds.add(recv);
                }
            }
        }
        else {
            for (Method caller : pta.callersOf(method)) {
                if (!visited.contains(caller)) {
                    visited.add(caller);
                    totalPreds.addAll(getPreds(caller));
                }
            }
        }
        return totalPreds;
    }
}
