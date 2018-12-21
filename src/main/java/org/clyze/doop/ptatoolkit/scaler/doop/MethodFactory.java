package org.clyze.doop.ptatoolkit.scaler.doop;

import org.clyze.doop.ptatoolkit.doop.DataBase;
import org.clyze.doop.ptatoolkit.doop.Query;
import org.clyze.doop.ptatoolkit.doop.basic.DoopInstanceMethod;
import org.clyze.doop.ptatoolkit.doop.basic.DoopStaticMethod;
import org.clyze.doop.ptatoolkit.doop.factory.ElementFactory;
import org.clyze.doop.ptatoolkit.doop.factory.VariableFactory;
import org.clyze.doop.ptatoolkit.pta.basic.Method;
import org.clyze.doop.ptatoolkit.pta.basic.Variable;

import java.util.HashMap;
import java.util.Map;

/**
 * Just generate the methods with necessary information.
 */
public class MethodFactory extends ElementFactory<Method> {

    private final Map<String, Variable> sig2this = new HashMap<>();

    MethodFactory(DataBase db, VariableFactory varFactory) {
        db.query(Query.THIS_VAR).forEachRemaining(list -> {
            String sig = list.get(0);
            Variable thisVar = varFactory.get(list.get(1));
            sig2this.put(sig, thisVar);
        });
    }

    @Override
    protected Method createElement(String sig) {
        // isPrivate does not matter in Scaler, so just set false
        Variable thisVar = sig2this.get(sig);
        if (thisVar != null) { // sig represents an instance method
            return new DoopInstanceMethod(sig, thisVar, null, null,
                    false, ++count);
        } else { // sig represents a static method
            return new DoopStaticMethod(sig, null, null,
                    false, ++count);
        }
    }
}
