package org.clyze.doop.soot;

import soot.*;
import soot.jimple.DefinitionStmt;
import soot.util.Chain;
import soot.shimple.*;

import java.util.*;

public class DoopRenamer {
    static protected void transform(Body body) {
        Set<Local> transformedLocals = new HashSet<Local>();
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
                DefinitionStmt def = (DefinitionStmt) u;
                Value assignee = def.getLeftOp();
                if (assignee instanceof Local) {
                    Local var = (Local) assignee;
                    if (!(var.getName().startsWith("$")) && !(transformedLocals.contains(var))) {
                        transformedLocals.add(var);
                        var.setName(var.getName() + "#_" + linenumberToRegister);
                    }
                }
            }
        }
    }
}
