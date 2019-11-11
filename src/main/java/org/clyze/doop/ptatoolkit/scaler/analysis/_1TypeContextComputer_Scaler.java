package org.clyze.doop.ptatoolkit.scaler.analysis;

import org.clyze.doop.ptatoolkit.Global;
import org.clyze.doop.ptatoolkit.pta.basic.Method;
import org.clyze.doop.ptatoolkit.scaler.doop.DoopPointsToAnalysis;

public class _1TypeContextComputer_Scaler extends ContextComputer {

    public _1TypeContextComputer_Scaler(DoopPointsToAnalysis pta, ObjectAllocationGraph oag) {
        super(pta, oag);
    }

    @Override
    public String getAnalysisName() {
        return "1-type";
    }

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
