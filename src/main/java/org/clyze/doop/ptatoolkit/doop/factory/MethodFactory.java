package org.clyze.doop.ptatoolkit.doop.factory;

import org.clyze.doop.ptatoolkit.doop.DataBase;
import org.clyze.doop.ptatoolkit.doop.Query;
import org.clyze.doop.ptatoolkit.doop.basic.DoopInstanceMethod;
import org.clyze.doop.ptatoolkit.doop.basic.DoopStaticMethod;
import org.clyze.doop.ptatoolkit.pta.basic.Method;
import org.clyze.doop.ptatoolkit.pta.basic.Variable;

import java.util.*;

/**
 * Factory class for creating Method instances based on the information stored in the database. This factory retrieves method information such as parameters, return variables, and method attributes (e.g., whether the method is private or implicitly reachable) from the database and constructs Method objects accordingly.
 */
public class MethodFactory extends ElementFactory<Method> {

    private final Map<String, Variable> sig2this = new HashMap<>();
    private final Map<String, Set<Variable>> sig2params = new HashMap<>();
    private final Map<String, Set<Variable>> sig2ret = new HashMap<>();
    private final Set<String> privateMethods = new HashSet<>();
    private final Set<String> implicitReachableMethods = new HashSet<>();

    /**
     * Constructor for MethodFactory that initializes the method signature to this variable, method signature to parameters, method signature to return variables, private methods, and implicitly reachable methods mappings from the database.
     * @param db the database containing the method information
     * @param varFactory the variable factory used to create variable instances from the database entries
     */
    public MethodFactory(DataBase db, VariableFactory varFactory) {
        db.query(Query.THIS_VAR).forEachRemaining(list -> {
            String sig = list.get(0);
            Variable thisVar = varFactory.get(list.get(1));
            sig2this.put(sig, thisVar);
        });

        db.query(Query.PARAMS).forEachRemaining(list -> {
            String sig = list.get(0);
            Variable param = varFactory.get(list.get(1));
            if (!sig2params.containsKey(sig)) {
                sig2params.put(sig, new HashSet<>(4));
            }
            sig2params.get(sig).add(param);
        });

        db.query(Query.RET_VARS).forEachRemaining(list -> {
            String sig = list.get(0);
            Variable ret = varFactory.get(list.get(1));
            if (!sig2ret.containsKey(sig)) {
                sig2ret.put(sig, new HashSet<>(4));
            }
            sig2ret.get(sig).add(ret);
        });

        db.query(Query.METHOD_MODIFIER).forEachRemaining(list -> {
            String sig = list.get(0);
            String mod = list.get(1);
            if (mod.equals("private")) {
                privateMethods.add(sig);
            }
        });

        db.query(Query.IMPLICITREACHABLE).forEachRemaining(list -> {
            String sig = list.get(0);
            implicitReachableMethods.add(sig);
        });
    }

    /** 
     * Creates a Method instance for the given method signature.
     * @param sig the method signature
     * @return a Method instance for the given method signature
     */
    @Override
    protected Method createElement(String sig) {
        Variable thisVar = sig2this.get(sig);
        Set<Variable> params = sig2params.get(sig);
        if (params == null) {
            params = Collections.emptySet();
        }
        Set<Variable> retVars = sig2ret.get(sig);
        if (retVars == null) {
            retVars = Collections.emptySet();
        }
        boolean isPrivate = privateMethods.contains(sig);
        if (thisVar != null) { // sig represents an instance method
            return new DoopInstanceMethod(sig, thisVar, params, retVars,
                    isPrivate, implicitReachableMethods.contains(sig), ++count);
        } else { // sig represents a static method
            return new DoopStaticMethod(sig, params, retVars,
                    isPrivate, implicitReachableMethods.contains(sig), ++count);
        }
    }

}
