package org.clyze.doop.ptatoolkit.scaler.doop;

import org.clyze.doop.ptatoolkit.doop.basic.DoopObj;
import org.clyze.doop.ptatoolkit.doop.factory.ElementFactory;
import org.clyze.doop.ptatoolkit.pta.basic.Obj;

public class ObjFactory extends ElementFactory<Obj> {

    @Override
    protected Obj createElement(String name) {
        return new DoopObj(name, null, ++count);
    }
}
