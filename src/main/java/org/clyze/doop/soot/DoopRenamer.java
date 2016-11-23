package org.clyze.doop.soot;

import soot.*;
import soot.jimple.DefinitionStmt;
import soot.util.Chain;

import java.util.*;

public class DoopRenamer
{
    static protected void transform(Body body)
    {
        Set<Local> transformedLocals = new HashSet<Local>();

        // For all statements, see whether they def a var.
        for (Unit u : body.getUnits()) {
            if (u instanceof DefinitionStmt) {
                DefinitionStmt def = (DefinitionStmt) u;
                Value assignee = def.getLeftOp();
                if (assignee instanceof Local) {
                    Local var = (Local) assignee;
                    if(!(var.getName().startsWith("$")) && !(transformedLocals.contains(var))) {
                        transformedLocals.add(var);
                        int lineNumber = u.getJavaSourceStartLineNumber();
                        if (lineNumber > 0)
                            var.setName(var.getName()+"#_"+u.getJavaSourceStartLineNumber());
                    }
                }
            }
        }
    }
}
