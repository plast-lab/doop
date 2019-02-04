package org.clyze.doop.ptatoolkit.scaler.analysis;

import org.clyze.doop.ptatoolkit.pta.basic.Method;
import org.clyze.doop.ptatoolkit.pta.basic.Obj;
import org.clyze.doop.ptatoolkit.scaler.doop.DoopPointsToAnalysis;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class ContextComputer {

    protected final DoopPointsToAnalysis pta;
    final ObjectAllocationGraph oag;
    private Map<Method, Long> method2ctxNumber = new HashMap<>();
    ContextComputer worstCaseContextComputer;
    PrintWriter writer = null;

    ContextComputer(DoopPointsToAnalysis pta, ObjectAllocationGraph oag) {
        this.pta = pta;
        this.oag = oag;
    }

    ContextComputer(DoopPointsToAnalysis pta, ObjectAllocationGraph oag, ContextComputer worstCaseContextComputer) {
        this.pta = pta;
        this.oag = oag;
        this.worstCaseContextComputer = worstCaseContextComputer;
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

    void computeContext() {
        for (Method method : pta.reachableMethods()) {
            method2ctxNumber.put(method, computeContextNumberOf(method));
        }
    }
}
