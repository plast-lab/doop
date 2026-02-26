package org.clyze.doop.ptatoolkit.pta.basic;

import java.util.Collection;

public abstract class InstanceCallSite extends CallSite {

    private final Variable receiver; // for static call, this field is null

    /**
     * Constructor for InstanceCallSite that initializes the receiver variable and the arguments of the call site. The receiver variable represents the object on which the instance method is called, while the args represent the arguments passed to the method.
     * @param receiver the variable representing the receiver of the instance method call
     * @param args the arguments passed to the instance method call
     */
    protected InstanceCallSite(Variable receiver, Collection<Variable> args) {
        super(args);
        this.receiver = receiver;
    }

    public Variable getReceiver() {
        return receiver;
    }

}
