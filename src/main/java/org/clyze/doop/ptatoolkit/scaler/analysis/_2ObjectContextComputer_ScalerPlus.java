package org.clyze.doop.ptatoolkit.scaler.analysis;

import org.clyze.doop.ptatoolkit.pta.basic.Method;
import org.clyze.doop.ptatoolkit.pta.basic.Obj;
import org.clyze.doop.ptatoolkit.pta.basic.Type;
import org.clyze.doop.ptatoolkit.scaler.doop.DoopPointsToAnalysis;
import org.clyze.doop.ptatoolkit.scaler.pta.PointsToAnalysis;

import java.io.*;
import java.util.*;

public class _2ObjectContextComputer_ScalerPlus extends ContextComputer {
    private Set<Method> visited = null;
    private Map<Method, Set<List<Obj>>> methodToContextMap = new HashMap<>();


    _2ObjectContextComputer_ScalerPlus(DoopPointsToAnalysis pta, ObjectAllocationGraph oag) {
        super(pta, oag);
        try {
            writer = new PrintWriter("scaler-two-object.csv");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getAnalysisName() {
        return "2-object";
    }

    @Override
    protected long computeContextNumberOf(Method method) {
        visited = new HashSet<>();

        if (method.isInstance()) {
            if (pta.receiverObjectsOf(method).isEmpty()) {
                System.out.printf("2object - Empty receiver: %s\n", method.toString());
                return 1;
            }
        }

        Set<List<Obj>> totalContexts = getContexts(method);
        for (List<Obj> contexts : totalContexts) {
            //System.out.println(method + "\t" + contexts.get(0) + "\t" + contexts.get(1));
            writer.println(method + "\t" + contexts.get(0) + "\t" + contexts.get(1));

        }
        if (!methodToContextMap.containsKey(method))
            methodToContextMap.put(method, totalContexts);

        long contextNumber = totalContexts.size();

        return  contextNumber > 0? contextNumber: 1;
    }

    private Set<List<Obj>> getContexts(Method method) {
        if (methodToContextMap.containsKey(method)) {
            return methodToContextMap.get(method);
        }

        Set<List<Obj>> contexts = new HashSet<>();
        if (method.isImplicitReachable()) {
            contexts.add(Arrays.asList(super.pta.objFactory.get("<<immutable context>>"), super.pta.objFactory.get("<<immutable context>>")));
        }

        visited.add(method);
        if (method.isInstance()) {

            for (Obj recv : pta.receiverObjectsOf(method)) {
                Set<Obj> preds = oag.predsOf(recv);
                if (!preds.isEmpty()) {
                    for (Obj pred : preds) {
                        contexts.add(Arrays.asList(super.pta.objFactory.get(pred.toString()), super.pta.objFactory.get(recv.toString())));
                        contexts.add(Arrays.asList(super.pta.objFactory.get("<<immutable context>>"), super.pta.objFactory.get(recv.toString())));
                        contexts.add(Arrays.asList(super.pta.objFactory.get("<<immutable hcontext>>"), super.pta.objFactory.get(recv.toString())));

                    }
                } else {
                    // without allocator, back to 1-object
                    contexts.add(Arrays.asList(super.pta.objFactory.get("<<immutable hcontext>>"), super.pta.objFactory.get(recv.toString())));
                }
            }
        }
        else {
            if (method.toString().contains("<java.util.Arrays: int[] copyOf(int[],int)>"))
                System.out.println("visited size: " + visited.size());
            for (Method caller : pta.callersOf(method)) {
                if (!visited.contains(caller)) {
                    visited.add(caller);
                    if (method.toString().contains("<java.util.Arrays: int[] copyOf(int[],int)>"))
                        System.out.println("Checking caller: " + caller + " contexts: " + getContexts(caller).size());
                    contexts.addAll(getContexts(caller));
                } else {
                    if (method.toString().contains("<java.util.Arrays: int[] copyOf(int[],int)>"))
                        System.out.println("Checking caller: " + caller + " - already visited");
                }
            }
            contexts.add(Arrays.asList(super.pta.objFactory.get("<<immutable context>>"), super.pta.objFactory.get("<<immutable context>>")));
        }
        return contexts;
    }
}
