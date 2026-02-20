package org.clyze.doop.ptatoolkit.doop.basic;

import org.clyze.doop.ptatoolkit.pta.basic.StaticMethod;
import org.clyze.doop.ptatoolkit.pta.basic.Variable;

import java.util.Collection;

public class DoopStaticMethod extends StaticMethod {

    private final String sig;
    private final int id;

    /**
     * Constructor for DoopStaticMethod that initializes the method signature, parameters, return variables, and method attributes.
     * @param sig the signature of the static method
     * @param params the parameters of the static method
     * @param retVars the return variables of the static method
     * @param isPrivate indicates whether the static method is private
     * @param isImplicitReachable indicates whether the static method is implicitly reachable
     * @param id the unique identifier of the static method
     */
    public DoopStaticMethod(String sig,
                            Collection<Variable> params,
                            Collection<Variable> retVars,
                            boolean isPrivate,
                            boolean isImplicitReachable,
                            int id) {
        super(params, retVars, isPrivate, isImplicitReachable);
        this.sig = sig;
        this.id = id;
    }

    /** Returns the unique identifier of the static method. */
    @Override
    public int getID() {
        return id;
    }

    /** Returns the string representation of the static method. */
    @Override
    public String toString() {
        return sig;
    }

}
