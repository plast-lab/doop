package org.clyze.doop.ptatoolkit.scaler.doop;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Streams;
import org.clyze.doop.ptatoolkit.Global;
import org.clyze.doop.ptatoolkit.doop.DataBase;
import org.clyze.doop.ptatoolkit.doop.Query;
import org.clyze.doop.ptatoolkit.doop.factory.TypeFactory;
import org.clyze.doop.ptatoolkit.doop.factory.VariableFactory;
import org.clyze.doop.ptatoolkit.pta.basic.*;
import org.clyze.doop.ptatoolkit.scaler.pta.PointsToAnalysis;
import org.clyze.doop.ptatoolkit.util.MutableLong;
import org.clyze.doop.ptatoolkit.util.Timer;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.clyze.doop.ptatoolkit.scaler.doop.Attribute.*;

public class DoopPointsToAnalysis implements PointsToAnalysis {

    private final DataBase db;
    private Set<Obj> allObjs;
    private Set<Method> reachableMethods;
    private Map<Method, Set<Method>> methodNeighborMap;
    private Map<Method, Integer> methodTotalVPTMap;
    private BiMap<Method, Integer> methodIdMap;
    // The following factories may be used by iterators
    private VariableFactory varFactory;
    public ObjFactory objFactory;
    public TypeFactory typeFactory;
    private Set<String> specialObjects;

    public DoopPointsToAnalysis(File database, String option) {
        Timer ptaTimer = new Timer("Points-to Analysis Timer");
        System.out.println("Reading points-to analysis results ... ");
        ptaTimer.start();
        this.db = new DataBase(database);
        if (option.equals("scaler")) {
            initScalerPostProcessing();
        }
//        else {
//            initScalerRankPostProcessing();
//        }
        ptaTimer.stop();
    }

    @Override
    public Set<Obj> allObjects() {
        return allObjs;
    }

    @Override
    public Set<Method> reachableMethods() {
        return reachableMethods;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Set<Obj> pointsToSetOf(Variable var) {
        if (var.hasAttribute(PTS)) {
            return (Set<Obj>) var.getAttribute(PTS);
        } else { // in this case, the variable is a null pointer
            if (Global.isDebug()) {
                System.out.println(var + " is a null pointer.");
            }
            return Collections.emptySet();
        }
    }

    @Override
    public long pointsToSetSizeOf(Variable var) {
        if (var.hasAttribute(PTS_SIZE)) {
            MutableLong size = (MutableLong) var.getAttribute(PTS_SIZE);
            return size.longValue();
        } else {
            return 0;
        }
    }

    @Override
    public Set<Variable> variablesDeclaredIn(Method method) {
        return method.getAttributeSet(VARS_IN);
    }

    @Override
    public Set<Obj> objectsAllocatedIn(Method method) {
        return method.getAttributeSet(ALLOCATED);
    }

    @Override
    public Set<Method> calleesOf(Method method) {
        return method.getAttributeSet(CALLEE);
    }

    @Override
    public Set<Method> callersOf(Method method) {
        return method.getAttributeSet(CALLER);
    }

    @Override
    public Set<Method> methodsInvokedOn(Obj obj) {
        return obj.getAttributeSet(MTD_ON);
    }

    @Override
    public Set<Obj> receiverObjectsOf(Method method) {
        return method.getAttributeSet(RECEIVER);
    }

    @Override
    public Type declaringAllocationTypeOf(Obj obj) {
        return (Type) obj.getAttribute(DECLARING_ALLOC_TYPE);
    }

    @Override
    public Type declaringTypeOf(Method method) {
        return (Type) method.getAttribute(DECLARING_TYPE);
    }

    private void initScalerPostProcessing() {
        typeFactory = new TypeFactory();
        varFactory = new VariableFactory();
        objFactory = new ObjFactory();
        MethodFactory mtdFactory = new MethodFactory(db, varFactory);

        // Set of variable names whose points-to sets may be needed
        Set<String> interestingVarNames = new HashSet<>();

        // obtain all reachable instance methods
        db.query(Query.INST_METHODS).forEachRemaining(list -> {
            String mtdSig = list.get(0);
            InstanceMethod instMtd = (InstanceMethod) mtdFactory.get(mtdSig);
            interestingVarNames.add(instMtd.getThis().toString());
        });

        buildPointsToSet(varFactory, objFactory, interestingVarNames);

        // compute the objects allocated in each method
        specialObjects = Streams.stream(db.query(Query.SPECIAL_OBJECTS))
                .map(list -> list.get(0))
                .collect(Collectors.toSet());
        computeAllocatedObjects(objFactory, mtdFactory);
        buildCalleesAndCallers(mtdFactory);
        buildMethodsInvokedOnObjects(mtdFactory);
        buildReceiverObjects();
        buildDeclaringAllocationType(objFactory, typeFactory);
        buildDeclaredVariables(mtdFactory, varFactory);
        buildDeclaringType(mtdFactory, typeFactory);
        buildMethodTotalVPTMap(mtdFactory);
    }

    /**
     * Build points-to sets of interesting variables. This method also computes
     * the size of points-to set for each variable (in instance method).
     */
    private void buildPointsToSet(VariableFactory varFactory, ObjFactory objFactory, Set<String> interestingVarNames) {
        allObjs = new HashSet<>();
        db.query(Query.Stats_Simple_InsensVarPointsTo).forEachRemaining(list -> {
            String objName = list.get(0);
            String varName = list.get(1);
            Obj obj = objFactory.get(objName);
            Variable var = varFactory.get(varName);
            if (!interestingVarNames.isEmpty()) {
                if (interestingVarNames.contains(varName)) {
                    // add points-to set to var as its attribute
                    var.addToAttributeSet(PTS, obj);
                }
            }
            else {
                var.addToAttributeSet(PTS, obj);
            }
            increasePointsToSetSizeOf(var);
            allObjs.add(obj);
        });
    }

    private void increasePointsToSetSizeOf(Variable var) {
        if (var.hasAttribute(PTS_SIZE)) {
            MutableLong size = (MutableLong) var.getAttribute(PTS_SIZE);
            size.increase();
        } else {
            var.setAttribute(PTS_SIZE, new MutableLong(1));
        }
    }

    private void computeAllocatedObjects(ObjFactory objFactory, MethodFactory mtdFactory) {
        db.query(Query.OBJECT_IN).forEachRemaining(list -> {
            String objName = list.get(0);
            if (isNormalObject(objName)) {
                Obj obj = objFactory.get(objName);
                Method method = mtdFactory.get(list.get(1));
                method.addToAttributeSet(ALLOCATED, obj);
                obj.setAttribute(ALLOCATED, method);
            }
        });
    }

    private boolean isNormalObject(String objName) {
        return !specialObjects.contains(objName) &&
                !objName.startsWith("<class "); // class constant
    }

    /**
     * Build caller-callee relations.
     */
    private void buildCalleesAndCallers(MethodFactory mtdFactory) {
        reachableMethods = new HashSet<>();
        Map<String, String> callIn = new HashMap<>();

        db.query(Query.CALLSITEIN).forEachRemaining(list -> {
            String call = list.get(0);
            String methodSig = list.get(1);
            callIn.put(call, methodSig);
        });

        db.query(Query.CALL_EDGE).forEachRemaining(list -> {
            String callerSig = callIn.get(list.get(0));
            if (callerSig != null) {
                Method caller = mtdFactory.get(callerSig);
                Method callee = mtdFactory.get(list.get(1));
                caller.addToAttributeSet(CALLEE, callee);
                callee.addToAttributeSet(CALLER, caller);
            } else if (Global.isDebug()) {
                System.out.println("Null caller of: " + list.get(0));
            }
        });

        db.query(Query.Reachable).forEachRemaining(list -> {
           reachableMethods.add(mtdFactory.get(list.get(0)));
        });
    }

    /**
     * Map each object to the methods invoked on it.
     */
    private void buildMethodsInvokedOnObjects(MethodFactory mtdFactory) {
        mtdFactory.getAllElements()
                .stream()
                .filter(Method::isInstance)
                .map(m -> (InstanceMethod) m)
                .forEach(instMtd -> {
                    Variable thisVar = instMtd.getThis();
                    if (pointsToSetOf(thisVar).isEmpty()) {
                        System.out.println("ERROR_- EMPTY RECEIVER this: " + thisVar);
                    }
                    pointsToSetOf(thisVar).forEach(obj -> {
                        obj.addToAttributeSet(MTD_ON, instMtd);
                    });
                });
    }

    /**
     * Map each object to the type which contains its allocation site.
     */
    private void buildDeclaringAllocationType(ObjFactory objFactory, TypeFactory typeFactory) {
        db.query(Query.DECLARING_CLASS_ALLOCATION).forEachRemaining(list -> {
            Obj obj = objFactory.get(list.get(0));
            Type type = typeFactory.get(list.get(1));
            obj.setAttribute(DECLARING_ALLOC_TYPE, type);
        });
    }

    /**
     * Map each instance method to their receiver objects.
     */
    private void buildReceiverObjects() {
        for (Obj obj : allObjects()) {
            for (Method method : methodsInvokedOn(obj)) {
                method.addToAttributeSet(RECEIVER, obj);
            }
        }
    }

    /**
     * Map each method to the variables declared in the method.
     */
    private void buildDeclaredVariables(MethodFactory mtdFactory, VariableFactory varFactory) {
        db.query(Query.VAR_IN).forEachRemaining(list -> {
            Variable var = varFactory.get(list.get(0));
            Method inMethod = mtdFactory.get(list.get(1));
                inMethod.addToAttributeSet(VARS_IN, var);
        });
    }

    /**
     * Map each method to the type which declares it.
     */
    private void buildDeclaringType(MethodFactory mtdFactory,
                                    TypeFactory typeFactory) {
        mtdFactory.getAllElements().forEach(m -> {
            String sig = m.toString();
            String typeName = sig.substring(1, sig.indexOf(':'));
            Type type = typeFactory.get(typeName);
            m.setAttribute(DECLARING_TYPE, type);
        });
    }

    private void buildMethodNeighborsMap(MethodFactory mtdFactory) {
        methodNeighborMap = new HashMap<>();

        db.query(Query.Method_Neighbor).forEachRemaining(list -> {
            Method method = mtdFactory.get(list.get(0));
            Method neighbor = mtdFactory.get(list.get(1));
            //System.out.println("Put (" + method + ", " + neighbor + ")");
            fillNeighborMap(method, neighbor, methodNeighborMap.get(method));

            fillNeighborMap(neighbor, method, methodNeighborMap.get(neighbor));

        });
        System.out.println("Method neighbors map total size: " + methodNeighborMap.keySet().size());
    }

    private void fillNeighborMap(Method method, Method neighbor, Set<Method> methods) {
        if(methodNeighborMap.containsKey(method)) {
            methods.add(neighbor);
        }
        else {
            Set<Method> neighborSet = new HashSet<>();
            neighborSet.add(neighbor);
            methodNeighborMap.put(method, neighborSet);
        }
    }

    private void buildMethodTotalVPTMap(MethodFactory mtdFactory) {
        methodIdMap = HashBiMap.create();
        methodTotalVPTMap = new HashMap<>();
        AtomicInteger id = new AtomicInteger(0);

        db.query(Query.Method_TotalVPT).forEachRemaining(list -> {
            Method method = mtdFactory.get(list.get(0));
            methodIdMap.put(method, id.getAndAdd(1));
            Integer totalVPT = Integer.parseInt(list.get(1));
            //System.out.println("Put (" + method + ", " + totalVPT + ")");
            methodTotalVPTMap.put(method, totalVPT);
        });
    }

    public Map<Method, Integer> getMethodTotalVPTMap() {
        return methodTotalVPTMap;
    }

    public BiMap<Method, Integer> getMethodIdMap() {
        return methodIdMap;
    }

    public Map<Method, Set<Method>> getMethodNeighborMap() {
        return methodNeighborMap;
    }
}
