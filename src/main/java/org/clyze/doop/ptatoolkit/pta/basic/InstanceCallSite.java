package org.clyze.doop.ptatoolkit.pta.basic;

import java.util.Collection;

public abstract class InstanceCallSite extends CallSite {

    private final Variable receiver; // for static call, this field is null

    protected InstanceCallSite(Variable receiver, Collection<Variable> args) {
        super(args);
        this.receiver = receiver;
    }

    public Variable getReceiver() {
        return receiver;
    }

}
