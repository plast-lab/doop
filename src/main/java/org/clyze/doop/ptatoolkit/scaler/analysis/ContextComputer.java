package org.clyze.doop.ptatoolkit.scaler.analysis;

import org.clyze.doop.ptatoolkit.pta.basic.Method;
import org.clyze.doop.ptatoolkit.scaler.doop.DoopPointsToAnalysis;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * Base class for computing per-method context counts for a context-sensitivity strategy.
 */
public abstract class ContextComputer {

    protected final DoopPointsToAnalysis pta;
    final ObjectAllocationGraph oag;
    private Map<Method, Long> method2ctxNumber = new HashMap<>();
    ContextComputer worstCaseContextComputer;
    PrintWriter writer = null;

    /**
     * Creates a context computer.
     *
     * @param pta the points-to analysis
     * @param oag the object allocation graph
     */
    ContextComputer(DoopPointsToAnalysis pta, ObjectAllocationGraph oag) {
        this.pta = pta;
        this.oag = oag;
    }

    /**
     * Creates a context computer with a fallback/worst-case strategy.
     *
     * @param pta the points-to analysis
     * @param oag the object allocation graph
     * @param worstCaseContextComputer the fallback context computer
     */
    ContextComputer(DoopPointsToAnalysis pta, ObjectAllocationGraph oag, ContextComputer worstCaseContextComputer) {
        this.pta = pta;
        this.oag = oag;
        this.worstCaseContextComputer = worstCaseContextComputer;
    }

    /**
     * Returns the computed context count of a method.
     *
     * @param method the method
     * @return the context count
     */
    public long contextNumberOf(Method method) {
        Long contextNumber = method2ctxNumber.get(method);
        if (contextNumber == null) {
            System.out.println("Method has null context number!!!!");
            return 0;
        }
        return contextNumber;
    }

    /**
     * Returns the strategy name.
     *
     * @return the analysis name
     */
    public abstract String getAnalysisName();

    /**
     * Computes the context count of a single method.
     *
     * @param method the method
     * @return the context count
     */
    protected abstract long computeContextNumberOf(Method method);

    /**
     * Computes and stores context counts for all reachable methods.
     */
    void computeContext() {
        for (Method method : pta.reachableMethods()) {
            method2ctxNumber.put(method, computeContextNumberOf(method));
        }
    }
}
