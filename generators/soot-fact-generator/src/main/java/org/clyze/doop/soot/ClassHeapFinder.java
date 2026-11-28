package org.clyze.doop.soot;

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import org.clyze.utils.TypeUtils;
import soot.ArrayType;
import soot.Body;
import soot.BooleanType;
import soot.ByteType;
import soot.CharType;
import soot.DoubleType;
import soot.FloatType;
import soot.IntType;
import soot.LongType;
import soot.RefType;
import soot.ShortType;
import soot.SootClass;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.VoidType;
import soot.jimple.AssignStmt;
import soot.jimple.ClassConstant;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;

/**
 * Collects the types that appear in class constants of method bodies. Bodies
 * are scanned as they are built during fact generation (they are released
 * afterwards), from several threads.
 */
class ClassHeapFinder {
    private final Set<String> classHeapTypes = ConcurrentHashMap.newKeySet();

    /**
     * Returns the heap types that appear in class constants of the scanned
     * bodies but are not among the given classes.
     *
     * @return the heap types
     */
    public Collection<String> getUnrecordedTypes(Iterable<SootClass> classes) {
        Collection<String> ret = new TreeSet<>(classHeapTypes);
        for (SootClass c : classes)
            ret.remove(c.getName());
        return ret;
    }

    void scan(Body b) {
        for (Unit u : b.getUnits())
            if (u instanceof AssignStmt) {
                Value right = ((AssignStmt)u).getRightOp();
                if (right instanceof ClassConstant)
                    processClassConstant((ClassConstant)right);
                else if (right instanceof InvokeExpr)
                    processInvokeExpr((InvokeExpr)right);
            } else if (u instanceof InvokeExpr)
                processInvokeExpr((InvokeExpr)u);
            else if (u instanceof InvokeStmt)
                processInvokeExpr(((InvokeStmt)u).getInvokeExpr());
    }

    private void processInvokeExpr(InvokeExpr invoke) {
        for (Value arg : invoke.getArgs())
            if (arg instanceof ClassConstant)
                processClassConstant((ClassConstant)arg);
    }

    private void processClassConstant(ClassConstant constant) {
        String s = TypeUtils.replaceSlashesWithDots(constant.getValue());
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
    public static Type raiseTypeWithSoot(String descriptor) {
        boolean isArray = false;

        int numDimensions;
        for(numDimensions = 0; descriptor.startsWith("["); descriptor = descriptor.substring(1)) {
            isArray = true;
            ++numDimensions;
        }

        Object baseType;
        if (descriptor.equals("B")) {
            baseType = ByteType.v();
        } else if (descriptor.equals("C")) {
            baseType = CharType.v();
        } else if (descriptor.equals("D")) {
            baseType = DoubleType.v();
        } else if (descriptor.equals("F")) {
            baseType = FloatType.v();
        } else if (descriptor.equals("I")) {
            baseType = IntType.v();
        } else if (descriptor.equals("J")) {
            baseType = LongType.v();
        } else if (descriptor.equals("V")) {
            baseType = VoidType.v();
        } else if (descriptor.startsWith("L")) {
            if (!descriptor.endsWith(";")) {
                throw new RuntimeException("Class reference does not end with ;");
            }

            String className = descriptor.substring(1, descriptor.length() - 1);
            baseType = RefType.v(className.replace('/', '.'));
        } else if (descriptor.equals("S")) {
            baseType = ShortType.v();
        } else {
            if (!descriptor.equals("Z")) {
                throw new RuntimeException("Unknown field type: " + descriptor);
            }

            baseType = BooleanType.v();
        }

        return (Type)(isArray ? ArrayType.v((Type)baseType, numDimensions) : baseType);
    }
}
