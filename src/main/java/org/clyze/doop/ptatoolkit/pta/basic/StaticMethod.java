package org.clyze.doop.ptatoolkit.pta.basic;

import java.util.Collection;

public abstract class StaticMethod extends Method {

    protected StaticMethod(Collection<Variable> params,
                           Collection<Variable> retVars,
                           boolean isPrivate) {
        super(params, retVars, isPrivate);
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
