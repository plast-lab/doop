package org.clyze.doop.ptatoolkit.scaler.doop;

import org.clyze.doop.ptatoolkit.doop.basic.DoopObj;
import org.clyze.doop.ptatoolkit.doop.factory.ElementFactory;
import org.clyze.doop.ptatoolkit.pta.basic.Obj;

/**
 * Factory class for creating Obj instances based on the information stored in the database. This factory retrieves object information such as type from the database and constructs Obj objects accordingly.
 */
public class ObjFactory extends ElementFactory<Obj> {

    /**
     * Creates an Obj instance for the given object signature.
     * @param name the object signature
     * @return an Obj instance for the given object signature
      */
    @Override
    protected Obj createElement(String name) {
        return new DoopObj(name, null, ++count);
    }
}
