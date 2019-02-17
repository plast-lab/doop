package org.clyze.doop.ptatoolkit.pta.basic;

import java.util.Collection;

public abstract class StaticMethod extends Method {

    protected StaticMethod(Collection<Variable> params,
                           Collection<Variable> retVars,
                           boolean isPrivate,
                           boolean isImplicitReachable) {
        super(params, retVars, isPrivate, isImplicitReachable);
    }

    @Override
    public Collection<Variable> getAllParameters() {
        return getParameters();
    }

    @Override
    public boolean isInstance() {
        return false;
    }

}
