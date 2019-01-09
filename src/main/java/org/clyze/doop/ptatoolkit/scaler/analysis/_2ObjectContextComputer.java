package org.clyze.doop.ptatoolkit.scaler.analysis;

import org.clyze.doop.ptatoolkit.Global;
import org.clyze.doop.ptatoolkit.scaler.pta.PointsToAnalysis;
import org.clyze.doop.ptatoolkit.pta.basic.Method;
import org.clyze.doop.ptatoolkit.pta.basic.Obj;

import java.util.HashSet;
import java.util.Set;

public class _2ObjectContextComputer extends ContextComputer {

    _2ObjectContextComputer(PointsToAnalysis pta, ObjectAllocationGraph oag) {
        super(pta, oag);
    }

    @Override
    public String getAnalysisName() {
        return "2-object";
    }

    @Override
    protected int computeContextNumberOf(Method method) {
        if (method.isInstance()) {
            if (pta.receiverObjectsOf(method).isEmpty()) {
                if (Global.isDebug()) {
                    System.out.printf("Empty receiver: %s\n", method.toString());
                }
                return 1;
            }
        }
        Set<Obj> totalPreds = getPreds(method);

        return totalPreds.size();
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
            for (Method caller : pta.calleesOf(method)) {
                totalPreds.addAll(getPreds(caller));
            }
        }
        return totalPreds;
    }
}
