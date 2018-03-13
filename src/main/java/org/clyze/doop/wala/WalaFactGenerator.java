package org.clyze.doop.wala;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.types.TypeReference;
import org.clyze.doop.soot.Session;
import soot.*;
import soot.jimple.AssignStmt;
import soot.jimple.IdentityStmt;
import soot.jimple.ReturnStmt;
import soot.jimple.ThrowStmt;

import java.util.Set;

/**
 * Traverses Soot classes and invokes methods in FactWriter to
 * generate facts. The class FactGenerator is the main class
 * controlling what facts are generated.
 */

class WalaFactGenerator implements Runnable {

    private WalaFactWriter _writer;
    private boolean _ssa;
    private Set<IClass> _iClasses;
    private final int maxRetries = 10;

    WalaFactGenerator(WalaFactWriter writer, Set<IClass> iClasses)
    {
        this._writer = writer;

        this._iClasses = iClasses;
    }

    @Override
    public void run() {

        for (IClass iClass : _iClasses) {
            _writer.writeClassOrInterfaceType(iClass);

            int modifiers = iClass.getModifiers();
            if(Modifier.isAbstract(modifiers))
                _writer.writeClassModifier(iClass, "abstract");
            if(Modifier.isFinal(modifiers))
                _writer.writeClassModifier(iClass, "final");
            if(Modifier.isPublic(modifiers))
                _writer.writeClassModifier(iClass, "public");
            if(Modifier.isPrivate(modifiers))
                _writer.writeClassModifier(iClass, "private");

            // the isInterface condition prevents Object as superclass of interface
            if (iClass.getSuperclass() != null && !iClass.isInterface()) {
                _writer.writeDirectSuperclass(iClass, iClass.getSuperclass());
            }

            for (IClass i : iClass.getAllImplementedInterfaces()) {
                _writer.writeDirectSuperinterface(iClass, i);
            }

            iClass.getAllFields().forEach(this::generate);

            boolean success;
            int numRetries = 0;
            do {
                success = true;
                try {
                    for (IMethod m : iClass.getAllMethods()) {
                        Session session = new Session();
                        try {
                            generate(m, session);
                        }
                        catch (Exception exc) {
                            // Map<Thread,StackTraceElement[]> liveThreads = Thread.getAllStackTraces();
                            // for (Iterator<Thread> i = liveThreads.keySet().iterator(); i.hasNext(); ) {
                            //     Thread key = i.next();
                            //     System.err.println("Thread " + key.getName());
                            //     StackTraceElement[] trace = liveThreads.getLibrary(key);
                            //     for (int j = 0; j < trace.length; j++) {
                            //         System.err.println("\tat " + trace[j]);
                            //     }
                            // }

                            System.err.println("Error while processing method: " + m);
                            throw exc;
                        }
                    }
                } catch (Exception exc) {
                    numRetries++;
                    if (numRetries > maxRetries) {
                        System.err.println("\nGiving up...\n");
                        throw exc;
                    }
                    else {
                        System.err.println("\nRETRYING\n");
                    }
                    success = false;
                }
            } while (!success);

        }
    }

    private void generate(IField f)
    {
        _writer.writeField(f);
        _writer.writeFieldInitialValue(f);

        //int modifiers = f.getModifiers();
//        if(Modifier.isAbstract(modifiers))
//            _writer.writeFieldModifier(f, "abstract");
//        if(Modifier.isFinal(modifiers))
//            _writer.writeFieldModifier(f, "final");
//        if(Modifier.isNative(modifiers))
//            _writer.writeFieldModifier(f, "native");
//        if(Modifier.isPrivate(modifiers))
//            _writer.writeFieldModifier(f, "private");
//        if(Modifier.isProtected(modifiers))
//            _writer.writeFieldModifier(f, "protected");
//        if(Modifier.isPublic(modifiers))
//            _writer.writeFieldModifier(f, "public");
//        if(Modifier.isStatic(modifiers))
//            _writer.writeFieldModifier(f, "static");
//        if(Modifier.isSynchronized(modifiers))
//            _writer.writeFieldModifier(f, "synchronized");
//        if(Modifier.isTransient(modifiers))
//            _writer.writeFieldModifier(f, "transient");
//        if(Modifier.isVolatile(modifiers))
//            _writer.writeFieldModifier(f, "volatile");
        // TODO interface?
        // TODO strictfp?
        // TODO annotation?
        // TODO enum?
    }


    /* Check if a Type refers to a phantom class */
    private static boolean phantomBased(Type t) {
        if (t instanceof RefLikeType) {
            if (t instanceof RefType)
                return ((RefType) t).getSootClass().isPhantom();
            else if (t instanceof ArrayType)
                return phantomBased(((ArrayType) t).getElementType());
        }
        return false;
    }

    public static boolean phantomBased(IMethod m) {
        /* Check for phantom classes */

//        if (m.isPhantom()) {
//            System.out.println("Method " + m.getSignature() + " is phantom.");
//            return true;
//        }
//
//        for(SootClass clazz: m.getExceptions())
//            if (clazz.isPhantom()) {
//                System.out.println("Class " + clazz.getName() + " is phantom.");
//                return true;
//            }
//
//        for(int i = 0 ; i < m.getParameterCount(); i++)
//            if(phantomBased(m.getParameterType(i))) {
//                System.out.println("Parameter type " + m.getParameterType(i) + " of " + m.getSignature() + " is phantom.");
//                return true;
//            }
//
//        if (phantomBased(m.getReturnType())) {
//            System.out.println("Return type " + m.getReturnType() + " of " + m.getSignature() + " is phantom.");
//            return true;
//        }

        return false;
    }

    void generate(IMethod m, Session session)
    {
        if (phantomBased(m)) {
            //m.setPhantom(true);
            return;
        }

        _writer.writeMethod(m);

        //int modifiers = m.getModifiers();

//        if(Modifier.isAbstract(modifiers))
//            _writer.writeMethodModifier(m, "abstract");
//        if(Modifier.isFinal(modifiers))
//            _writer.writeMethodModifier(m, "final");
//        if(Modifier.isNative(modifiers))
//            _writer.writeMethodModifier(m, "native");
//        if(Modifier.isPrivate(modifiers))
//            _writer.writeMethodModifier(m, "private");
//        if(Modifier.isProtected(modifiers))
//            _writer.writeMethodModifier(m, "protected");
//        if(Modifier.isPublic(modifiers))
//            _writer.writeMethodModifier(m, "public");
//        if(Modifier.isStatic(modifiers))
//            _writer.writeMethodModifier(m, "static");
//        if(Modifier.isSynchronized(modifiers))
//            _writer.writeMethodModifier(m, "synchronized");
//        // TODO would be nice to have isVarArgs in Soot
//        if(Modifier.isTransient(modifiers))
//            _writer.writeMethodModifier(m, "varargs");
//        // TODO would be nice to have isBridge in Soot
//        if(Modifier.isVolatile(modifiers))
//            _writer.writeMethodModifier(m, "bridge");
//        // TODO interface?
//        // TODO strictfp?
//        // TODO annotation?
//        // TODO enum?

        if(!m.isStatic())
        {
            _writer.writeThisVar(m);
        }

        if(m.isNative())
        {
            _writer.writeNativeReturnVar(m);
        }

        for(int i = 0 ; i < m.getNumberOfParameters(); i++)
        {
            _writer.writeFormalParam(m, i);
        }

        try {
            for(TypeReference exceptionType: m.getDeclaredExceptions())
            {
                _writer.writeMethodDeclaresException(m, exceptionType);
            }
        } catch (InvalidClassFileException e) {
            e.printStackTrace();
        }

        if(!(m.isAbstract() || m.isNative()))
        {
//            if(!m.get)
//            {
//                // This instruction is the bottleneck of
//                // soot-fact-generation.
//                // synchronized(Scene.v()) {
//                System.out.println("You should never see this anymore");
//                m.retrieveActiveBody();
//                // } // synchronizing so broadly = giving up on Soot's races
//            }
//
//            Body b = m.getActiveBody();
//            try {
//                if (b != null) {
//                    if (_ssa) {
//                        b = Shimple.v().newBody(b);
//                        m.setActiveBody(b);
//                    }
//                    DoopRenamer.transform(b);
//                    generate(m, b, session);
//                }
//            } catch (RuntimeException ex) {
//                System.err.println("Fact generation failed for method " + m.getSignature() + ":");
//                ex.printStackTrace();
//            }
        }
    }

    private void generate(IMethod m, Body b, Session session)
    {

//        for(Local l : b.getLocals())
//        {
//            _writer.writeLocal(m, l);
//        }
//
//        IrrelevantStmtSwitch sw =  new IrrelevantStmtSwitch();
//        for(Unit u : b.getUnits())
//        {
//            Stmt stmt = (Stmt) u;
//
//            stmt.apply(sw);
//
//            if(sw.relevant)
//            {
//                if(stmt instanceof AssignStmt)
//                {
//                    generate(m, (AssignStmt) stmt, session);
//                }
//                else if(stmt instanceof IdentityStmt)
//                {
//                    generate(m, (IdentityStmt) stmt, session);
//                }
//                else if(stmt instanceof InvokeStmt)
//                {
//                    _writer.writeInvoke(m, stmt, stmt.getInvokeExpr(), session);
//                }
//                else if(stmt instanceof ReturnStmt)
//                {
//                    generate(m, (ReturnStmt) stmt, session);
//                }
//                else if(stmt instanceof ReturnVoidStmt)
//                {
//                    _writer.writeReturnVoid(m, stmt, session);
//                }
//                else if(stmt instanceof ThrowStmt)
//                {
//                    generate(m, (ThrowStmt) stmt, session);
//                }
//                else if(stmt instanceof GotoStmt)
//                {
//                    // processed in second run: we might not know the number of
//                    // the unit yet.
//                    session.calcUnitNumber(stmt);
//                }
//                else if(stmt instanceof IfStmt)
//                {
//                    // processed in second run: we might not know the number of
//                    // the unit yet.
//                    session.calcUnitNumber(stmt);
//                }
//                else if(stmt instanceof EnterMonitorStmt)
//                {
//                    //TODO: how to handle EnterMonitorStmt when op is not a Local?
//                    if (((EnterMonitorStmt) stmt).getOp() instanceof Local)
//                        _writer.writeEnterMonitor(m, stmt, (Local) ((EnterMonitorStmt) stmt).getOp(), session);
//                }
//                else if(stmt instanceof ExitMonitorStmt)
//                {
//                    //TODO: how to handle ExitMonitorStmt when op is not a Local?
//                    if (((ExitMonitorStmt) stmt).getOp() instanceof Local)
//                        _writer.writeExitMonitor(m, stmt, (Local) ((ExitMonitorStmt) stmt).getOp(), session);
//                }
//                else if(stmt instanceof TableSwitchStmt)
//                {
//                    // same as IfStmt and GotoStmt.
//                    session.calcUnitNumber(stmt);
//                }
//                else if(stmt instanceof LookupSwitchStmt)
//                {
//                    session.calcUnitNumber(stmt);
//                }
//                else if (stmt instanceof NopStmt) {
//                    session.calcUnitNumber(stmt);
//                }
//                else
//                {
//                    throw new RuntimeException("Cannot handle statement: " + stmt);
//                }
//            }
//            else
//            {
//                // only reason for assign or invoke statements to be irrelevant
//                // is the invocation of a method on a phantom class
//                if(stmt instanceof AssignStmt)
//                    _writer.writeAssignPhantomInvoke(m, stmt, session);
//                else if (stmt instanceof InvokeStmt)
//                    _writer.writePhantomInvoke(m, stmt, session);
//                else if (stmt instanceof BreakpointStmt)
//                    _writer.writeBreakpointStmt(m, stmt, session);
//                else
//                    throw new RuntimeException("Unexpected irrelevant statement: " + stmt);
//            }
//        }
//
//        for(Unit u : b.getUnits())
//        {
//            Stmt stmt = (Stmt) u;
//
//            if(stmt instanceof GotoStmt)
//            {
//                _writer.writeGoto(m, stmt, ((GotoStmt) stmt).getTarget(), session);
//            }
//            else if(stmt instanceof IfStmt)
//            {
//                _writer.writeIf(m, stmt, ((IfStmt) stmt).getTarget(), session);
//            }
//            else if(stmt instanceof TableSwitchStmt)
//            {
//                _writer.writeTableSwitch(m, (TableSwitchStmt) stmt, session);
//            }
//            else if(stmt instanceof LookupSwitchStmt)
//            {
//                _writer.writeLookupSwitch(m, (LookupSwitchStmt) stmt, session);
//            }
//        }
//
//        Trap previous = null;
//        for(Trap t : b.getTraps())
//        {
//            _writer.writeExceptionHandler(m, t, session);
//            if(previous != null)
//            {
//                _writer.writeExceptionHandlerPrevious(m, t, previous, session);
//            }
//
//            previous = t;
//        }
    }

    /**
     * Assignment statement
     */
    public void generate(SootMethod inMethod, AssignStmt stmt, Session session)
    {
        Value left = stmt.getLeftOp();

        if(left instanceof Local)
        {
            generateLeftLocal(inMethod, stmt, session);
        }
        else
        {
            generateLeftNonLocal(inMethod, stmt, session);
        }
    }

    private void generateLeftLocal(SootMethod inMethod, AssignStmt stmt, Session session)
    {
//        Local left = (Local) stmt.getLeftOp();
//        Value right = stmt.getRightOp();
//
//        if(right instanceof Local)
//        {
//            _writer.writeAssignLocal(inMethod, stmt, left, (Local) right, session);
//        }
//        else if(right instanceof InvokeExpr)
//        {
//            _writer.writeAssignInvoke(inMethod, stmt, left, (InvokeExpr) right, session);
//        }
//        else if(right instanceof NewExpr)
//        {
//            _writer.writeAssignHeapAllocation(inMethod, stmt, left, (NewExpr) right, session);
//        }
//        else if(right instanceof NewArrayExpr)
//        {
//            _writer.writeAssignHeapAllocation(inMethod, stmt, left, (NewArrayExpr) right, session);
//        }
//        else if(right instanceof NewMultiArrayExpr)
//        {
//            _writer.writeAssignNewMultiArrayExpr(inMethod, stmt, left, (NewMultiArrayExpr) right, session);
//        }
//        else if(right instanceof StringConstant)
//        {
//            _writer.writeAssignStringConstant(inMethod, stmt, left, (StringConstant) right, session);
//        }
//        else if(right instanceof ClassConstant)
//        {
//            _writer.writeAssignClassConstant(inMethod, stmt, left, (ClassConstant) right, session);
//        }
//        else if(right instanceof NumericConstant)
//        {
//            _writer.writeAssignNumConstant(inMethod, stmt, left, (NumericConstant) right, session);
//        }
//        else if(right instanceof NullConstant)
//        {
//            _writer.writeAssignNull(inMethod, stmt, left, session);
//            // NoNullSupport: use the line below to remove Null Constants from the facts.
//            // _writer.writeUnsupported(inMethod, stmt, session);
//        }
//        else if(right instanceof InstanceFieldRef)
//        {
//            InstanceFieldRef ref = (InstanceFieldRef) right;
//            _writer.writeLoadInstanceField(inMethod, stmt, ref.getField(), (Local) ref.getBase(), left, session);
//        }
//        else if(right instanceof StaticFieldRef)
//        {
//            StaticFieldRef ref = (StaticFieldRef) right;
//            _writer.writeLoadStaticField(inMethod, stmt, ref.getField(), left, session);
//        }
//        else if(right instanceof ArrayRef)
//        {
//            ArrayRef ref = (ArrayRef) right;
//            Local base = (Local) ref.getBase();
//            Value index = ref.getIndex();
//
//            if(index instanceof Local)
//            {
//                    _writer.writeLoadArrayIndex(inMethod, stmt, base, left, (Local) index, session);
//            }
//            else if(index instanceof IntConstant)
//            {
//                    _writer.writeLoadArrayIndex(inMethod, stmt, base, left, null, session);
//            }
//            else
//            {
//                throw new RuntimeException("Cannot handle assignment: " + stmt + " (index: " + index.getClass() + ")");
//            }
//        }
//        else if(right instanceof CastExpr)
//        {
//            CastExpr cast = (CastExpr) right;
//            Value op = cast.getOp();
//
//            if(op instanceof Local)
//            {
//                _writer.writeAssignCast(inMethod, stmt, left, (Local) op, cast.getCastType(), session);
//            }
//            else if(op instanceof NumericConstant)
//            {
//                // seems to always get optimized out, do we need this?
//                _writer.writeAssignCastNumericConstant(inMethod, stmt, left, (NumericConstant) op, cast.getCastType(), session);
//            }
//            else if (op instanceof NullConstant || op instanceof  ClassConstant || op instanceof  StringConstant)
//            {
//                _writer.writeAssignCastNull(inMethod, stmt, left, cast.getCastType(), session);
//            }
//            else
//            {
//                throw new RuntimeException("Cannot handle assignment: " + stmt + " (op: " + op.getClass() + ")");
//            }
//        }
//        else if(right instanceof PhiExpr)
//        {
//            for(Value alternative : ((PhiExpr) right).getValues())
//            {
//                _writer.writeAssignLocal(inMethod, stmt, left, (Local) alternative, session);
//            }
//        }
//        else if (right instanceof BinopExpr)
//        {
//            _writer.writeAssignBinop(inMethod, stmt, left, (BinopExpr) right, session);
//        }
//        else if (right instanceof UnopExpr)
//        {
//            _writer.writeAssignUnop(inMethod, stmt, left, (UnopExpr) right, session);
//        }
//        else if (right instanceof InstanceOfExpr)
//        {
//            InstanceOfExpr expr = (InstanceOfExpr) right;
//            if (expr.getOp() instanceof Local)
//                _writer.writeAssignInstanceOf(inMethod, stmt, left, (Local) expr.getOp(), expr.getCheckType(), session);
//            else // TODO check if this is possible (instanceof on something that is not a local var)
//                _writer.writeUnsupported(inMethod, stmt, session);
//        }
//        else
//        {
//            throw new RuntimeException("Cannot handle assignment: " + stmt + " (right: " + right.getClass() + ")");
//        }
    }

    private void generateLeftNonLocal(SootMethod inMethod, AssignStmt stmt, Session session)
    {
//        Value left = stmt.getLeftOp();
//        Value right = stmt.getRightOp();
//
//        // first make sure we have local variable for the right-hand-side.
//        Local rightLocal = null;
//
//        if(right instanceof Local)
//        {
//            rightLocal = (Local) right;
//        }
//        else if(right instanceof StringConstant)
//        {
//            rightLocal = _writer.writeStringConstantExpression(inMethod, stmt, (StringConstant) right, session);
//        }
//        else if(right instanceof NumericConstant)
//        {
//            rightLocal = _writer.writeNumConstantExpression(inMethod, stmt, (NumericConstant) right, session);
//        }
//        else if(right instanceof NullConstant)
//        {
//            rightLocal = _writer.writeNullExpression(inMethod, stmt, left.getType(), session);
//            // NoNullSupport: use the line below to remove Null Constants from the facts.
//            // _writer.writeUnsupported(inMethod, stmt, session);
//        }
//        else if(right instanceof ClassConstant)
//        {
//            rightLocal = _writer.writeClassConstantExpression(inMethod, stmt, (ClassConstant) right, session);
//        }
//        else
//        {
//            throw new RuntimeException("Cannot handle rhs: " + stmt + " (right: " + right.getClass() + ")");
//        }
//
//        // arrays
//        //
//        // NoNullSupport: use the line below to remove Null Constants from the facts.
//        // if(left instanceof ArrayRef && rightLocal != null)
//        if(left instanceof ArrayRef)
//        {
//            ArrayRef ref = (ArrayRef) left;
//            Local base = (Local) ref.getBase();
//            Value index = ref.getIndex();
//
//            if (index instanceof Local)
//                _writer.writeStoreArrayIndex(inMethod, stmt, base, rightLocal, (Local) index, session);
//            else
//                _writer.writeStoreArrayIndex(inMethod, stmt, base, rightLocal, null, session);
//        }
//        // NoNullSupport: use the line below to remove Null Constants from the facts.
//        // else if(left instanceof InstanceFieldRef && rightLocal != null)
//        else if(left instanceof InstanceFieldRef)
//        {
//            InstanceFieldRef ref = (InstanceFieldRef) left;
//            _writer.writeStoreInstanceField(inMethod, stmt, ref.getField(), (Local) ref.getBase(), rightLocal, session);
//        }
//        // NoNullSupport: use the line below to remove Null Constants from the facts.
//        // else if(left instanceof StaticFieldRef && rightLocal != null)
//        else if(left instanceof StaticFieldRef)
//        {
//            StaticFieldRef ref = (StaticFieldRef) left;
//            _writer.writeStoreStaticField(inMethod, stmt, ref.getField(), rightLocal, session);
//        }
//        // NoNullSupport: use the else part below to remove Null Constants from the facts.
//        /*else if(right instanceof NullConstant)
//        {
//            _writer.writeUnsupported(inMethod, stmt, session);
//            // skip, not relevant for pointer analysis
//        }*/
//        else
//        {
//            throw new RuntimeException("Cannot handle assignment: " + stmt + " (right: " + right.getClass() + ")");
//        }
    }

    private void generate(SootMethod inMethod, IdentityStmt stmt, Session session)
    {
//        Value left = stmt.getLeftOp();
//        Value right = stmt.getRightOp();
//
//        if(right instanceof CaughtExceptionRef) {
//            // make sure we can jump to statement we do not care about (yet)
//            _writer.writeUnsupported(inMethod, stmt, session);
//
//            /* Handled by ExceptionHandler generation (ExceptionHandler:FormalParam).
//
//               TODO Would be good to check more carefully that a caught
//               exception does not occur anywhere else.
//            */
//            return;
//        }
//        else if(left instanceof Local && right instanceof ThisRef)
//        {
//            _writer.writeAssignLocal(inMethod, stmt, (Local) left, (ThisRef) right, session);
//        }
//        else if(left instanceof Local && right instanceof ParameterRef)
//        {
//            _writer.writeAssignLocal(inMethod, stmt, (Local) left, (ParameterRef) right, session);
//        }
//        else
//        {
//            throw new RuntimeException("Cannot handle identity statement: " + stmt);
//        }
    }

    /**
     * Return statement
     */
    private void generate(SootMethod inMethod, ReturnStmt stmt, Session session)
    {
//        Value v = stmt.getOp();
//
//        if(v instanceof Local)
//        {
//            _writer.writeReturn(inMethod, stmt, (Local) v, session);
//        }
//        else if(v instanceof StringConstant)
//        {
//            Local tmp = _writer.writeStringConstantExpression(inMethod, stmt, (StringConstant) v, session);
//            _writer.writeReturn(inMethod, stmt, tmp, session);
//        }
//        else if(v instanceof ClassConstant)
//        {
//            Local tmp = _writer.writeClassConstantExpression(inMethod, stmt, (ClassConstant) v, session);
//            _writer.writeReturn(inMethod, stmt, tmp, session);
//        }
//        else if(v instanceof NumericConstant)
//        {
//            Local tmp = _writer.writeNumConstantExpression(inMethod, stmt, (NumericConstant) v, session);
//            _writer.writeReturn(inMethod, stmt, tmp, session);
//        }
//        else if(v instanceof NullConstant)
//        {
//            Local tmp = _writer.writeNullExpression(inMethod, stmt, inMethod.getReturnType(), session);
//            _writer.writeReturn(inMethod, stmt, tmp, session);
//            // NoNullSupport: use the line below to remove Null Constants from the facts.
//            // _writer.writeUnsupported(inMethod, stmt, session);
//        }
//        else
//        {
//            throw new RuntimeException("Unhandled return statement: " + stmt);
//        }
    }

    private void generate(SootMethod inMethod, ThrowStmt stmt, Session session)
    {
//        Value v = stmt.getOp();
//
//        if(v instanceof Local)
//        {
//            _writer.writeThrow(inMethod, stmt, (Local) v, session);
//        }
//        else if(v instanceof NullConstant)
//        {
//            _writer.writeThrowNull(inMethod, stmt, session);
//        }
//        else
//        {
//            throw new RuntimeException("Unhandled throw statement: " + stmt);
//        }
    }
}

