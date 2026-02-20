package org.clyze.doop.ptatoolkit.doop.factory;

import org.clyze.doop.ptatoolkit.doop.basic.DoopVariable;
import org.clyze.doop.ptatoolkit.pta.basic.Variable;

public class VariableFactory extends ElementFactory<Variable> {

    /**
     * Creates a new DoopVariable with the given name and a unique identifier.
     * @param name the name of the variable
     */
    @Override
    protected Variable createElement(String name) {
        return new DoopVariable(name, ++count);
    }

}
