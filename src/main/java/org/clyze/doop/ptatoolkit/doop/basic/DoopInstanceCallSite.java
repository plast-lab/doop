package org.clyze.doop.ptatoolkit.doop.basic;

import org.clyze.doop.ptatoolkit.pta.basic.InstanceCallSite;
import org.clyze.doop.ptatoolkit.pta.basic.Variable;

import java.util.Collection;

public class DoopInstanceCallSite extends InstanceCallSite {

    private final String callSite;
    private final int id;

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
