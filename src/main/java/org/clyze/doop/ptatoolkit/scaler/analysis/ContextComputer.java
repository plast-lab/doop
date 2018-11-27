package org.clyze.doop.ptatoolkit.scaler.analysis;

import org.clyze.doop.ptatoolkit.scaler.pta.PointsToAnalysis;
import org.clyze.doop.ptatoolkit.pta.basic.Method;

import java.util.HashMap;
import java.util.Map;

public abstract class ContextComputer {

    protected final PointsToAnalysis pta;
    protected final ObjectAllocationGraph oag;
    protected final Map<Method, Integer> method2ctxNumber = new HashMap<>();

    protected ContextComputer(PointsToAnalysis pta, ObjectAllocationGraph oag) {
        this.pta = pta;
        this.oag = oag;
        computeContext();
    }

    public int contextNumberOf(Method method) {
        return method2ctxNumber.get(method);
    }

    public abstract String getAnalysisName();

    protected abstract int computeContextNumberOf(Method method);

    private void computeContext() {
        for (Method method : pta.reachableMethods()) {
            if (method.isInstance()) {
                method2ctxNumber.put(method, computeContextNumberOf(method));
            }
        }
    }
}
