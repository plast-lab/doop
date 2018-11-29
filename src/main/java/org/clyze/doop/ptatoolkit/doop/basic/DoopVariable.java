package org.clyze.doop.ptatoolkit.doop.basic;

import org.clyze.doop.ptatoolkit.pta.basic.Variable;

public class DoopVariable extends Variable {

    private final String varName;
    private final int id;

    public DoopVariable(String varName, int id) {
        this.varName = varName;
        this.id = id;
    }

    @Override
    public int getID() {
        return id;
    }

    @Override
    public String toString() {
        return varName;
    }

}
