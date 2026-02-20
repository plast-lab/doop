package org.clyze.doop.ptatoolkit.doop.factory;

import org.clyze.doop.ptatoolkit.doop.basic.DoopType;
import org.clyze.doop.ptatoolkit.pta.basic.Type;

public class TypeFactory extends ElementFactory<Type> {

    /**
     * Creates a new DoopType with the given name and a unique identifier.
     * @param name the name of the type
     * @return a new DoopType instance with the specified name and a unique identifier
     */
    @Override
    protected Type createElement(String name) {
        return new DoopType(name, ++count);
    }
}
