package org.clyze.doop.ptatoolkit.scaler.analysis;

import org.clyze.doop.ptatoolkit.Global;
import org.clyze.doop.ptatoolkit.pta.basic.Method;
import org.clyze.doop.ptatoolkit.pta.basic.Obj;
import org.clyze.doop.ptatoolkit.pta.basic.Type;
import org.clyze.doop.ptatoolkit.scaler.doop.DoopPointsToAnalysis;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * This class implements a 2-object-sensitive context computation strategy. The context of a method is represented as a set of pairs of objects, where each pair consists of the receiver object and one of its predecessor objects in the object allocation graph. If a method has no receiver objects, it is given a default context. If a receiver object has no predecessors, the context includes the receiver object paired with itself. The number of contexts for a method is determined by the number of unique pairs of objects in its context set.
 */
public class _2TypeContextComputer_Scaler extends ContextComputer {

    public _2TypeContextComputer_Scaler(DoopPointsToAnalysis pta, ObjectAllocationGraph oag) {
        super(pta, oag);
    }

    @Override
    public String getAnalysisName() {
        return "2-type";
    }

    @Override
    protected long computeContextNumberOf(Method method) {
        if (pta.receiverObjectsOf(method).isEmpty()) {
            if (Global.isDebug()) {
                System.out.printf("Empty receiver: %s\n", method.toString());
            }
            return 1;
        }
        Set<List<Type>> contexts = new HashSet<>();
        for (Obj recv : pta.receiverObjectsOf(method)) {
            Set<Obj> preds = oag.predsOf(recv);
            if (!preds.isEmpty()) {
                for (Obj pred : preds) {
                    contexts.add(Arrays.asList(
                            pta.declaringAllocationTypeOf(pred),
                            pta.declaringAllocationTypeOf(recv)));
                }
            } else {
                // without allocator, back to 1-type
                contexts.add(Arrays.asList(
                        pta.declaringAllocationTypeOf(recv)));
            }
        }
        return contexts.size();
    }
}
