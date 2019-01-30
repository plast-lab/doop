package org.clyze.doop.ptatoolkit.doop.basic;

import org.clyze.doop.ptatoolkit.pta.basic.InstanceMethod;
import org.clyze.doop.ptatoolkit.pta.basic.Variable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class DoopInstanceMethod extends InstanceMethod {

    private final String sig;
    private final int id;
    private final List<Variable> allParams;

    public DoopInstanceMethod(
            String sig, Variable thisVar, Collection<Variable> params,
            Collection<Variable> retVars, boolean isPrivate, boolean isImplicitReachable,
            int id) {
        super(thisVar, params, retVars, isPrivate, isImplicitReachable);
        this.sig = sig;
        this.id = id;
        if (params != null) {
            allParams = new ArrayList<>(4);
            allParams.add(thisVar);
            allParams.addAll(params);
        } else {
            allParams = Collections.singletonList(thisVar);
        }
    }

    @Override
    public int getID() {
        return id;
    }

    @Override
    public String toString() {
        return sig;
    }

    @Override
    public Collection<Variable> getAllParameters() {
        return allParams;
    }

}
