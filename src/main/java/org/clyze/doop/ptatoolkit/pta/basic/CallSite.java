package org.clyze.doop.ptatoolkit.pta.basic;

import java.util.Collection;

public abstract class CallSite extends AttributeElement {

    private final Collection<Variable> arguments;

    protected CallSite(Collection<Variable> args) {
        this.arguments = args;
    }

    public Collection<Variable> getArguments() {
        return arguments;
    }

}
