package org.clyze.doop.ptatoolkit.scaler.analysis;

import org.clyze.doop.ptatoolkit.scaler.pta.PointsToAnalysis;
import org.clyze.doop.ptatoolkit.util.graph.DirectedGraph;
import org.clyze.doop.ptatoolkit.pta.basic.Method;
import org.clyze.doop.ptatoolkit.pta.basic.Obj;
import java.util.*;

public class ObjectAllocationGraph implements DirectedGraph<Obj> {

    private static final String PREDS = "Predecessors";
    private static final String SUCCS = "Successors";

    private final PointsToAnalysis pta;

    ObjectAllocationGraph(PointsToAnalysis pta) {
        this.pta = pta;
        init();
    }

    @Override
    public Set<Obj> allNodes() {
        return pta.allObjects();
    }

    @Override
    public Set<Obj> predsOf(Obj obj) {
        return obj.getAttributeSet(PREDS);
    }

    @Override
    public Set<Obj> succsOf(Obj obj) {
        return obj.getAttributeSet(SUCCS);
    }

    private void init() {
        Map<Obj, Set<Method>> invokedMethods = computeInvokedMethods();
        invokedMethods.forEach((obj, methods) -> methods.stream()
                .map(pta::objectsAllocatedIn)
                .flatMap(Collection::stream)
                .forEach(o -> {
                    obj.addToAttributeSet(SUCCS, o);
                    o.addToAttributeSet(PREDS, obj);
                }));
    }

    private Map<Obj, Set<Method>> computeInvokedMethods() {
        Map<Obj, Set<Method>> invokedMethods = new HashMap<>();
        pta.allObjects().forEach(obj -> {
            Set<Method> methods = new HashSet<>();
            Queue<Method> queue = new LinkedList<>(pta.methodsInvokedOn(obj));

            while (!queue.isEmpty()) {
                Method method = queue.poll();
                methods.add(method);

                /* Propagate to all static methods called by method invoked on obj */
                pta.calleesOf(method).stream()
                        .filter(m -> m.isStatic() && !methods.contains(m))
                        .forEach(queue::offer);
            }
            invokedMethods.put(obj, methods);
        });
        return invokedMethods;
    }
}
