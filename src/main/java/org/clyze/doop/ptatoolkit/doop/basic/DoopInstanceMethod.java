package org.clyze.doop.ptatoolkit.doop.basic;

import org.clyze.doop.ptatoolkit.pta.basic.InstanceMethod;
import org.clyze.doop.ptatoolkit.pta.basic.Variable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class DoopInstanceMethod extends InstanceMethod {

    private final String sig;
    private final int id;
    private final List<Variable> allParams;

    /**
     * Constructor for DoopInstanceMethod that initializes the method signature, parameters, return variables, and method attributes.
     * @param sig the signature of the instance method
     * @param thisVar the variable representing the 'this' reference in the instance method
     * @param params the parameters of the instance method
     * @param retVars the return variables of the instance method
     * @param isPrivate indicates whether the instance method is private
     * @param isImplicitReachable indicates whether the instance method is implicitly reachable
     * @param id the unique identifier of the instance method
     */
    public DoopInstanceMethod(
            String sig, Variable thisVar, Collection<Variable> params,
            Collection<Variable> retVars, boolean isPrivate, boolean isImplicitReachable,
            int id) {
        super(thisVar, params, retVars, isPrivate, isImplicitReachable);
        this.sig = sig;
        this.id = id;
        if (params != null) {
            allParams = new ArrayList<>(4);
            allParams.add(thisVar);
            allParams.addAll(params);
        } else {
            allParams = Collections.singletonList(thisVar);
        }
    }

    /* Returns the unique identifier of the instance method. */
    @Override
    public int getID() {
        return id;
    }

    /* Returns the string representation of the instance method. */
    @Override
    public String toString() {
        return sig;
    }

    /* Returns all parameters of the instance method including 'this'. */
    @Override
    public Collection<Variable> getAllParameters() {
        return allParams;
    }
}
