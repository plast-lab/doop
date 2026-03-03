package org.clyze.doop.ptatoolkit.pta.basic;

import java.util.Collection;

public abstract class InstanceMethod extends Method {

    private final Variable thisVar; // for static method, this field is null;

    /**
     * Constructor for InstanceMethod that initializes the receiver variable (thisVar), parameters, return variables, and method attributes. The thisVar represents the receiver of the instance method call, while the params and retVars represent the parameters and return variables of the method, respectively. The isPrivate and isImplicitReachable flags indicate whether the method is private and whether it is implicitly reachable.
     * @param thisVar the variable representing the 'this' reference in the instance method
     * @param params the parameters of the instance method
     * @param retVars the return variables of the instance method
     * @param isPrivate indicates whether the instance method is private
     * @param isImplicitReachable indicates whether the instance method is implicitly reachable
     */
    protected InstanceMethod(Variable thisVar,
                             Collection<Variable> params,
                             Collection<Variable> retVars,
                             boolean isPrivate,
                             boolean isImplicitReachable) {
        super(params, retVars, isPrivate, isImplicitReachable);
        this.thisVar = thisVar;
    }

    /**
     * Returns the variable representing the 'this' reference in the instance method.
     * @return the variable representing the 'this' reference in the instance method
     */
    public Variable getThis() {
        return thisVar;
    }

    @Override
    public boolean isInstance() {
        return true;
    }

}
