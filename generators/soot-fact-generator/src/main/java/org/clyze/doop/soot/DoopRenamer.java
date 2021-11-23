package org.clyze.doop.soot;

import java.util.Collection;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import soot.*;
import soot.jimple.DefinitionStmt;
import soot.shimple.Shimple;

class DoopRenamer {
    static void transform(Body body) {
        Collection<Local> transformedLocals = ConcurrentHashMap.<Local>newKeySet();
        int linenumber = 0;

        // For all statements, see whether they def a var.
        for (Unit u : body.getUnits()) {
            int potentialNextLineNumber = u.getJavaSourceStartLineNumber();
            if (potentialNextLineNumber > 0) {
                linenumber = potentialNextLineNumber;
            }
            int linenumberToRegister = linenumber;
            if (Shimple.isPhiNode(u)) {
                linenumberToRegister = linenumber + 1; // hack to compensate for lack of source for phi
            }
            if (u instanceof DefinitionStmt) {
                for (ValueBox valueBox : u.getDefBoxes()) {
                    Value value = valueBox.getValue();
                    if (value instanceof  Local) {
                        Local defVar = (Local) value;
                        String name = defVar.getName();
                        if (!(name.startsWith("$") || (name.startsWith("tmp$"))) && !(transformedLocals.contains(defVar))) {
                            transformedLocals.add(defVar);
                            defVar.setName(name + "#_" + linenumberToRegister);
                        }
                    }
                }
            }
        }
    }
}
