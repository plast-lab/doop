package org.clyze.doop.ptatoolkit.scaler.analysis;

import org.clyze.doop.ptatoolkit.pta.basic.Method;
import org.clyze.doop.ptatoolkit.scaler.pta.PointsToAnalysis;

import java.util.HashMap;
import java.util.Map;

public abstract class ContextComputer {

    protected final PointsToAnalysis pta;
    final ObjectAllocationGraph oag;
    private Map<Method, Integer> method2ctxNumber = new HashMap<>();

    ContextComputer(PointsToAnalysis pta, ObjectAllocationGraph oag) {
        this.pta = pta;
        this.oag = oag;
        computeContext();
    }

    public int contextNumberOf(Method method) {
        Integer contextNumber = method2ctxNumber.get(method);
        if (contextNumber == null) {
            return 0;
        }
        return contextNumber;
    }

    public abstract String getAnalysisName();

    protected abstract int computeContextNumberOf(Method method);

    private void computeContext() {
        for (Method method : pta.reachableMethods()) {
            method2ctxNumber.put(method, computeContextNumberOf(method));
        }
    }
}
