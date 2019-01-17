package org.clyze.doop.ptatoolkit.scaler.analysis;

import org.clyze.doop.ptatoolkit.pta.basic.Method;
import org.clyze.doop.ptatoolkit.scaler.pta.PointsToAnalysis;

import java.util.HashMap;
import java.util.Map;

public abstract class ContextComputer {

    protected final PointsToAnalysis pta;
    final ObjectAllocationGraph oag;
    private Map<Method, Long> method2ctxNumber = new HashMap<>();
    protected ContextComputer worstCaseContextComputer;


    ContextComputer(PointsToAnalysis pta, ObjectAllocationGraph oag) {
        this.pta = pta;
        this.oag = oag;
        computeContext();
    }

    ContextComputer(PointsToAnalysis pta, ObjectAllocationGraph oag, ContextComputer worstCaseContextComputer) {
        this.pta = pta;
        this.oag = oag;
        this.worstCaseContextComputer = worstCaseContextComputer;
        computeContext();
    }

    public long contextNumberOf(Method method) {
        Long contextNumber = method2ctxNumber.get(method);
        if (contextNumber == null) {
            System.out.println("Method has null context number!!!!");
            return 0;
        }
        return contextNumber;
    }

    public abstract String getAnalysisName();

    protected abstract long computeContextNumberOf(Method method);

    private void computeContext() {
        for (Method method : pta.reachableMethods()) {
            method2ctxNumber.put(method, computeContextNumberOf(method));
        }
    }
}
