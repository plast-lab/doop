package org.clyze.doop.soot;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import org.clyze.doop.util.TypeUtils;
import soot.*;
import soot.jimple.*;

class ClassHeapFinder {
    private final Collection<String> recordedTypes = new LinkedList<>();
    private final Collection<String> classHeapTypes = new LinkedList<>();

    /**
     * Returns the heap types that appear in class constants.
     *
     * @return the heap types
     */
    public Collection<String> getUnrecordedTypes(Collection<SootClass> classes) {
        scan(classes);
        Set<String> ret = new HashSet<>(classHeapTypes);
        ret.removeAll(recordedTypes);
        return ret;
    }

    private void scan(Iterable<SootClass> classes) {
        for (SootClass c : classes) {
            recordedTypes.add(c.getName()); 
            for (SootMethod m : c.getMethods())
                if (!(m.isPhantom() || m.isAbstract() || m.isNative()))
                    scan(m);
        }
    }

    private void scan(SootMethod m) {
        if (!m.hasActiveBody()) {
            m.retrieveActiveBody();
            System.err.println("Preprocessing: found method without active body: " + m.getSignature());
        }
        for (Unit u : m.getActiveBody().getUnits())
            if (u instanceof AssignStmt) {
                Value right = ((AssignStmt)u).getRightOp();
                if (right instanceof ClassConstant)
                    processClassConstant((ClassConstant)right);
            }
    }

    private void processClassConstant(ClassConstant constant) {
        String s = constant.getValue().replace('/', '.');
        char first = s.charAt(0);
        if (TypeUtils.isLowLevelType(first, s)) {
            // array type
            Type t = raiseTypeWithSoot(s);
            String actualType = t.toString();
            if (actualType.endsWith("[]")) {
                String elemType = actualType.substring(0, actualType.length() - 2);
                if (!TypeUtils.isPrimitiveType(elemType))
                    classHeapTypes.add(elemType);
            } else
                classHeapTypes.add(actualType);
        } else if (first != '(')   // Ignore method type constants
            classHeapTypes.add(s);
    }

    /**
     * Use Soot machinery to translate a low-level type id to a Type.
     *
     * @param s  the low-level JVM id of the type
     * @return   a Soot Type
     */
    public static Type raiseTypeWithSoot(String s) {
        return soot.coffi.Util.v().jimpleTypeOfFieldDescriptor(s);
    }
}
