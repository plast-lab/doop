package org.clyze.doop.soot;

import soot.*;
import soot.jimple.DefinitionStmt;
import soot.shimple.Shimple;

import java.util.HashSet;
import java.util.Set;

public class DoopRenamer {
    static protected void transform(Body body) {
        Set<Local> transformedLocals = new HashSet<>();
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
                        if (!(defVar.getName().startsWith("$") || (defVar.getName().startsWith("tmp$"))) && !(transformedLocals.contains(defVar))) {
                            transformedLocals.add(defVar);
                            defVar.setName(defVar.getName() + "#_" + linenumberToRegister);
                        }
                    }
                }
            }
        }
    }
}
