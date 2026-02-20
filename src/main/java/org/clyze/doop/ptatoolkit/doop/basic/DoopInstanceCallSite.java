package org.clyze.doop.ptatoolkit.doop.basic;

import org.clyze.doop.ptatoolkit.pta.basic.InstanceCallSite;
import org.clyze.doop.ptatoolkit.pta.basic.Variable;

import java.util.Collection;

public class DoopInstanceCallSite extends InstanceCallSite {

    private final String callSite;
    private final int id;

    /**
     * Constructor for DoopInstanceCallSite that initializes the call site, receiver variable, argument variables, and unique identifier.
     * @param callSite the string representation of the call site
     * @param receiver the variable representing the receiver of the instance method call
     * @param args the collection of variables representing the arguments of the instance method call
     * @param id the unique identifier of the instance call site
     */
    public DoopInstanceCallSite(String callSite, Variable receiver, Collection<Variable> args, int id) {
        super(receiver, args);
        this.callSite = callSite;
        this.id = id;
    }

    @Override
    public int getID() {
        return id;
    }

    @Override
    public String toString() {
        return callSite;
    }
}
