package org.clyze.doop.ptatoolkit.doop.factory;

import org.clyze.doop.ptatoolkit.doop.basic.DoopType;
import org.clyze.doop.ptatoolkit.pta.basic.Type;

public class TypeFactory extends ElementFactory<Type> {

    @Override
    protected Type createElement(String name) {
        return new DoopType(name, ++count);
    }
}
