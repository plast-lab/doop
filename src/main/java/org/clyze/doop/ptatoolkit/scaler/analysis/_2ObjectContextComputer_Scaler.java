package org.clyze.doop.ptatoolkit.scaler.analysis;

import org.clyze.doop.ptatoolkit.Global;
import org.clyze.doop.ptatoolkit.pta.basic.Method;
import org.clyze.doop.ptatoolkit.pta.basic.Obj;
import org.clyze.doop.ptatoolkit.scaler.doop.DoopPointsToAnalysis;

import java.util.Set;

/**
 * Computes 2-object context counts for methods.
 */
public class _2ObjectContextComputer_Scaler extends ContextComputer {

    /**
     * Creates a 2-object context computer.
     *
     * @param pta the points-to analysis
     * @param oag the object allocation graph
     */
    public _2ObjectContextComputer_Scaler(DoopPointsToAnalysis pta, ObjectAllocationGraph oag) {
        super(pta, oag);
    }

    /**
     * Returns the strategy name.
     *
     * @return {@code "2-object"}
     */
    @Override
    public String getAnalysisName() {
        return "2-object";
    }

    /**
     * Computes the number of 2-object contexts for the method.
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
        int count = 0;
        for (Obj recv : pta.receiverObjectsOf(method)) {
            Set<Obj> preds = oag.predsOf(recv);
            if (!preds.isEmpty()) {
                count += preds.size();
            } else {
                // without allocator, back to 1-object
                ++count;
            }
        }
        return count;
    }
}
