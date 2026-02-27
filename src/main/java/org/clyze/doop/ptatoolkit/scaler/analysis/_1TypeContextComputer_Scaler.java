package org.clyze.doop.ptatoolkit.scaler.analysis;

import org.clyze.doop.ptatoolkit.Global;
import org.clyze.doop.ptatoolkit.pta.basic.Method;
import org.clyze.doop.ptatoolkit.scaler.doop.DoopPointsToAnalysis;

/**
 * Computes 1-type context counts for methods.
 */
public class _1TypeContextComputer_Scaler extends ContextComputer {

    /**
     * Creates a 1-type context computer.
     *
     * @param pta the points-to analysis
     * @param oag the object allocation graph
     */
    public _1TypeContextComputer_Scaler(DoopPointsToAnalysis pta, ObjectAllocationGraph oag) {
        super(pta, oag);
    }

    /**
     * Returns the strategy name.
     *
     * @return {@code "1-type"}
     */
    @Override
    public String getAnalysisName() {
        return "1-type";
    }

    /**
     * Computes the number of distinct allocation-declaring types among receivers.
     *
     * @param method the method
     * @return the context count
     */
    @Override
    protected long computeContextNumberOf(Method method) {
        if (pta.receiverObjectsOf(method).isEmpty()) {
            if (Global.isDebug()) {
                System.out.printf("Empty receiver: %s\n", method.toString());
            }
            return 1;
        }
        return (int) pta.receiverObjectsOf(method).stream()
                .map(pta::declaringAllocationTypeOf)
                .distinct()
                .count();
    }
}
