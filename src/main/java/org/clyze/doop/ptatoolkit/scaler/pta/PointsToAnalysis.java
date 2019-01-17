package org.clyze.doop.ptatoolkit.scaler.pta;

import com.google.common.collect.BiMap;
import org.clyze.doop.ptatoolkit.pta.basic.Method;
import org.clyze.doop.ptatoolkit.pta.basic.Obj;
import org.clyze.doop.ptatoolkit.pta.basic.Type;
import org.clyze.doop.ptatoolkit.pta.basic.Variable;

import java.util.Map;
import java.util.Set;

public interface PointsToAnalysis {

    // For points-to set.
    /**
     *
     * @return all objects in the points-to analysis
     */
    Set<Obj> allObjects();

    /**
     *
     * @param var
     * @return the objects pointed by variable var,
     * i.e., the points-to set of var
     */
    Set<Obj> pointsToSetOf(Variable var);

    /**
     *
     * @param var
     * @return the size of (i.e., number of objects in)
     * points-to set of var
     */
    long pointsToSetSizeOf(Variable var);

    /**
     *
     * @param method
     * @return the variables declared in method
     */
    Set<Variable> variablesDeclaredIn(Method method);

    // For object allocation relations.
    /**
     *
     * @param method
     * @return the objects allocated in method
     */
    Set<Obj> objectsAllocatedIn(Method method);


    // For method calls.
    /**
     *
     * @param method
     * @return the callee methods of method
     */
    Set<Method> calleesOf(Method method);

    /**
     *
     * @param method
     * @return the caller methods of method
     */
    Set<Method> callersOf(Method method);

    /**
     *
     * @return all reachable methods in points-to analysis
     */
    Set<Method> reachableMethods();


    /**
     *
     * @param obj
     * @return the methods whose this variable points-to obj
     */
    Set<Method> methodsInvokedOn(Obj obj);

    /**
     *
     * @param method
     * @return the receiver objects of method
     */
    Set<Obj> receiverObjectsOf(Method method);

    // For types.
    /**
     *
     * @param obj
     * @return the type which contains the allocation site of obj
     */
    Type declaringAllocationTypeOf(Obj obj);

    /**
     *
     * @param method
     * @return the type that declares method
     */
    Type declaringTypeOf(Method method);

    Map<Method, Set<Method>> getMethodNeighborMap();
    BiMap<Method, Integer> getMethodIdMap();
    Map<Method, Integer> getMethodTotalVPTMap();
}
