package org.clyze.doop.ptatoolkit.pta.basic;

import java.util.Collection;

public abstract class InstanceMethod extends Method {

    private final Variable thisVar; // for static method, this field is null;

    protected InstanceMethod(Variable thisVar,
                             Collection<Variable> params,
                             Collection<Variable> retVars,
                             boolean isPrivate,
                             boolean isImplicitReachable) {
        super(params, retVars, isPrivate, isImplicitReachable);
        this.thisVar = thisVar;
    }

    public Variable getThis() {
        return thisVar;
    }

    @Override
    public boolean isInstance() {
        return true;
    }

}
