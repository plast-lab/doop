package org.clyze.doop.ptatoolkit.scaler.analysis;

import org.clyze.doop.ptatoolkit.pta.basic.Method;
import org.clyze.doop.ptatoolkit.pta.basic.Obj;
import org.clyze.doop.ptatoolkit.scaler.doop.DoopPointsToAnalysis;
import org.clyze.doop.ptatoolkit.scaler.pta.PointsToAnalysis;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        List<Obj> totalPreds = getPreds(method);
        long contextNumber = totalPreds.size();

        return  contextNumber > 0? contextNumber: 1;
    }

    private List<Obj> getPreds(Method method) {
        List<Obj> totalPreds = new ArrayList<>();

        if (method.isInstance()) {
            visited.add(method);

            for (Obj recv : pta.receiverObjectsOf(method)) {
                Set<Obj> preds = oag.predsOf(recv);
                if (!preds.isEmpty()) {
                    totalPreds.addAll(preds);
                    totalPreds.add(super.pta.objFactory.get("immutable context" + recv));
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
                    totalPreds.add(super.pta.objFactory.get("initial immutable context"));
                }
            }
        }
        return totalPreds;
    }
}
