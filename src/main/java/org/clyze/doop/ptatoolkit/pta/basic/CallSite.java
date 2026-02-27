package org.clyze.doop.ptatoolkit.pta.basic;

import java.util.Collection;

public abstract class CallSite extends AttributeElement {

    private final Collection<Variable> arguments;

    /**
     * Constructor for CallSite that initializes the arguments of the call site. The arguments represent the variables passed to the method being called at this call site.
     * @param args the arguments passed to the method call at this call site
     */
    protected CallSite(Collection<Variable> args) {
        this.arguments = args;
    }

    /**
     * Returns the collection of variables representing the arguments passed to the method call at this call site.
     * @return the collection of variables representing the arguments passed to the method call at this call site
     */
    public Collection<Variable> getArguments() {
        return arguments;
    }

}
