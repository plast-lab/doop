package org.clyze.doop.ptatoolkit.scaler.analysis;

import org.clyze.doop.ptatoolkit.Global;
import org.clyze.doop.ptatoolkit.pta.basic.Obj;
import org.clyze.doop.ptatoolkit.scaler.pta.PointsToAnalysis;
import org.clyze.doop.ptatoolkit.pta.basic.Method;

import java.util.HashSet;
import java.util.Set;

public class _1TypeContextComputer extends ContextComputer {

    _1TypeContextComputer(PointsToAnalysis pta, ObjectAllocationGraph oag) {
        super(pta, oag);
    }

    @Override
    public String getAnalysisName() {
        return "1-type";
    }

    @Override
    protected int computeContextNumberOf(Method method) {
        Set<Obj> totalReceiverObjects = getReceiverObjects(method);
        if (method.isInstance()) {
            if (totalReceiverObjects.isEmpty()) {
                if (Global.isDebug()) {
                    System.out.printf("Empty receiver: %s\n", method.toString());
                }
                return 1;
            }
        }

        return (int) totalReceiverObjects.stream()
                    .map(pta::declaringAllocationTypeOf)
                    .distinct()
                    .count();
    }

    private Set<Obj> getReceiverObjects(Method method) {
        Set<Obj> totalReceiverObjects = new HashSet<>();
        if (method.isInstance()) {
            pta.receiverObjectsOf(method);
        }
        else {
            for (Method caller : pta.calleesOf(method)) {
                totalReceiverObjects.addAll(pta.receiverObjectsOf(caller));
            }
        }
        return totalReceiverObjects;
    }
}
