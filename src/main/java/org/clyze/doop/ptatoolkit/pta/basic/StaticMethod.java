package org.clyze.doop.ptatoolkit.pta.basic;

import java.util.Collection;

/**
 * Base class for static methods in the points-to model.
 */
public abstract class StaticMethod extends Method {

    /**
     * Creates a static method abstraction.
     *
     * @param params the formal parameters
     * @param retVars the return variables
     * @param isPrivate whether the method is private
     * @param isImplicitReachable whether the method is implicitly reachable
     */
    protected StaticMethod(Collection<Variable> params,
                           Collection<Variable> retVars,
                           boolean isPrivate,
                           boolean isImplicitReachable) {
        super(params, retVars, isPrivate, isImplicitReachable);
    }

    /**
     * Returns all parameters for this static method.
     *
     * @return all parameters
     */
    @Override
    public Collection<Variable> getAllParameters() {
        return getParameters();
    }

    /**
     * Indicates that this method is not an instance method.
     *
     * @return {@code false}
     */
    @Override
    public boolean isInstance() {
        return false;
    }
}
