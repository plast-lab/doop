package org.clyze.doop.ptatoolkit.doop.basic;

import org.clyze.doop.ptatoolkit.pta.basic.Variable;

public class DoopVariable extends Variable {

    private final String varName;
    private final int id;

    /**
     * Constructor for DoopVariable that initializes the variable name and unique identifier.
     * @param varName the name of the variable
     * @param id the unique identifier of the variable
     */
    public DoopVariable(String varName, int id) {
        this.varName = varName;
        this.id = id;
    }

    /* Returns the unique identifier of the variable. */
    @Override
    public int getID() {
        return id;
    }

    /* Returns the string representation of the variable. */
    @Override
    public String toString() {
        return varName;
    }

}
