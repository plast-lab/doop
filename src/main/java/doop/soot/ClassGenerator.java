package doop.soot;

import soot.*;
import soot.jimple.*;
import soot.shimple.PhiExpr;
import soot.shimple.Shimple;

/**
 * Created by jimouris on 12/22/15.
 */
public class ClassGenerator implements Runnable {

    protected FactWriter _writer;
    protected boolean _ssa;
    SootClass _sootClass;

    public ClassGenerator(FactWriter writer, boolean ssa, SootClass sootClass)
    {
        this._writer = writer;
        this._ssa = ssa;
        this._sootClass = sootClass;
    }

    @Override
    public void run() {
        for(SootMethod m : _sootClass.getMethods())
        {
            Session session = new Session();

            try {
                generate(m, session); // try multithread this
            } catch (RuntimeException exc) {
                System.err.println("Error while processing method: " + m);
                throw exc;
            }
        }
    }

    /* Check if a Type refers to a phantom class */
    private boolean phantomBased(Type t) {
        if (t instanceof RefLikeType) {
            if (t instanceof RefType)
                return ((RefType) t).getSootClass().isPhantom();
            else if (t instanceof ArrayType)
                return phantomBased(((ArrayType) t).getElementType());
        }
        return false;
    }

    private boolean phantomBased(SootMethod m) {
        /* Check for phantom classes */

        if (m.isPhantom())
            return true;

        for(SootClass clazz: m.getExceptions())
            if (clazz.isPhantom())
                return true;

        for(int i = 0 ; i < m.getParameterCount(); i++)
            if(phantomBased(m.getParameterType(i)))
                return true;

        if (phantomBased(m.getReturnType()))
            return true;

        return false;
    }

    public void generate(SootMethod m, Session session)
    {
        if (phantomBased(m)) {
            //m.setPhantom(true);
            return;
        }

        _writer.writeMethodSignature(m);

        int modifiers = m.getModifiers();
        if(Modifier.isAbstract(modifiers))
            _writer.writeMethodModifier(m, "abstract");
        if(Modifier.isFinal(modifiers))
            _writer.writeMethodModifier(m, "final");
        if(Modifier.isNative(modifiers))
            _writer.writeMethodModifier(m, "native");
        if(Modifier.isPrivate(modifiers))
            _writer.writeMethodModifier(m, "private");
        if(Modifier.isProtected(modifiers))
            _writer.writeMethodModifier(m, "protected");
        if(Modifier.isPublic(modifiers))
            _writer.writeMethodModifier(m, "public");
        if(Modifier.isStatic(modifiers))
            _writer.writeMethodModifier(m, "static");
        if(Modifier.isSynchronized(modifiers))
            _writer.writeMethodModifier(m, "synchronized");
        // TODO would be nice to have isVarArgs in Soot
        if(Modifier.isTransient(modifiers))
            _writer.writeMethodModifier(m, "varargs");
        // TODO would be nice to have isBridge in Soot
        if(Modifier.isVolatile(modifiers))
            _writer.writeMethodModifier(m, "bridge");
        // TODO interface?
        // TODO strictfp?
        // TODO annotation?
        // TODO enum?

        if(!m.isStatic())
        {
            _writer.writeThisVar(m);
        }

        if(m.isNative())
        {
            _writer.writeNativeReturnVar(m);
        }

        for(int i = 0 ; i < m.getParameterCount(); i++)
        {
            _writer.writeFormalParam(m, i);
        }

        for(SootClass clazz: m.getExceptions())
        {
            _writer.writeMethodDeclaresException(m, clazz);
        }

        if(!(m.isAbstract() || m.isNative()))
        {
            if(!m.hasActiveBody())
            {
                // This instruction is the bottleneck of
                // soot-fact-generation. It accounts for more than 80%
                // of its total execution time. However, it is soot
                // internal so we'll need a profiler to optimize it.
                m.retrieveActiveBody();
            }

            Body b = m.getActiveBody();
            if(_ssa)
            {
                b = Shimple.v().newBody(b);
                m.setActiveBody(b);
            }

            generate(m, b, session);

            m.releaseActiveBody();
        }
    }

    public void generate(SootMethod m, Body b, Session session)
    {
        //TODO: Identify the problem with the jimple body of this method.
        if (!m.getDeclaration().equals("public java.lang.Object launch(java.net.URLConnection, java.io.InputStream, sun.net.www.MimeTable) throws sun.net.www.ApplicationLaunchException"))
            b.validate();

        for(Local l : b.getLocals())
        {
            _writer.writeLocal(m, l);
        }

        IrrelevantStmtSwitch sw =  new IrrelevantStmtSwitch();
        for(Unit u : b.getUnits())
        {
            Stmt stmt = (Stmt) u;

            stmt.apply(sw);

            if(sw.relevant)
            {
                if(stmt instanceof AssignStmt)
                {
                    generate(m, (AssignStmt) stmt, session);
                }
                else if(stmt instanceof IdentityStmt)
                {
                    generate(m, (IdentityStmt) stmt, session);
                }
                else if(stmt instanceof InvokeStmt)
                {
                    _writer.writeInvoke(m, stmt, ((InvokeStmt) stmt).getInvokeExpr(), session);
                }
                else if(stmt instanceof ReturnStmt)
                {
                    generate(m, (ReturnStmt) stmt, session);
                }
                else if(stmt instanceof ReturnVoidStmt)
                {
                    _writer.writeReturnVoid(m, stmt, session);
                }
                else if(stmt instanceof ThrowStmt)
                {
                    generate(m, (ThrowStmt) stmt, session);
                }
                else if(stmt instanceof GotoStmt)
                {
                    // processed in second run: we might not know the number of
                    // the unit yet.
                    session.calcUnitNumber(stmt);
                }
                else if(stmt instanceof IfStmt)
                {
                    // processed in second run: we might not know the number of
                    // the unit yet.
                    session.calcUnitNumber(stmt);
                }
                else if(stmt instanceof EnterMonitorStmt)
                {
                    //TODO: how to handle EnterMonitorStmt when op is not a Local?
                    if (((EnterMonitorStmt) stmt).getOp() instanceof Local)
                        _writer.writeEnterMonitor(m, stmt, (Local) ((EnterMonitorStmt) stmt).getOp(), session);
                }
                else if(stmt instanceof ExitMonitorStmt)
                {
                    //TODO: how to handle ExitMonitorStmt when op is not a Local?
                    if (((ExitMonitorStmt) stmt).getOp() instanceof Local)
                        _writer.writeExitMonitor(m, stmt, (Local) ((ExitMonitorStmt) stmt).getOp(), session);
                }
                else if(stmt instanceof TableSwitchStmt)
                {
                    // same as IfStmt and GotoStmt.
                    session.calcUnitNumber(stmt);
                }
                else if(stmt instanceof LookupSwitchStmt)
                {
                    session.calcUnitNumber(stmt);
                }
                else
                {
                    throw new RuntimeException("Cannot handle statement: " + stmt);
                }
            }
            else
            {
                // make sure we can jump to statement we do not care about (yet)
                _writer.writeUnsupported(m, stmt, session);
            }
        }

        for(Unit u : b.getUnits())
        {
            Stmt stmt = (Stmt) u;

            if(stmt instanceof GotoStmt)
            {
                _writer.writeGoto(m, stmt, ((GotoStmt) stmt).getTarget(), session);
            }
            else if(stmt instanceof IfStmt)
            {
                _writer.writeIf(m, stmt, ((IfStmt) stmt).getTarget(), session);
            }
            else if(stmt instanceof TableSwitchStmt)
            {
                _writer.writeTableSwitch(m, (TableSwitchStmt) stmt, session);
            }
            else if(stmt instanceof LookupSwitchStmt)
            {
                _writer.writeLookupSwitch(m, (LookupSwitchStmt) stmt, session);
            }
        }

        Trap previous = null;
        for(Trap t : b.getTraps())
        {
            _writer.writeExceptionHandler(m, t, session);
            if(previous != null)
            {
                _writer.writeExceptionHandlerPrevious(m, t, previous, session);
            }

            previous = t;
        }
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

    public void generateLeftLocal(SootMethod inMethod, AssignStmt stmt, Session session)
    {
        Local left = (Local) stmt.getLeftOp();
        Value right = stmt.getRightOp();

        if(right instanceof Local)
        {
            _writer.writeAssignLocal(inMethod, stmt, left, (Local) right, session);
        }
        else if(right instanceof InvokeExpr)
        {
            _writer.writeAssignInvoke(inMethod, stmt, left, (InvokeExpr) right, session);
        }
        else if(right instanceof NewExpr)
        {
            _writer.writeAssignHeapAllocation(inMethod, stmt, left, (NewExpr) right, session);
        }
        else if(right instanceof NewArrayExpr)
        {
            _writer.writeAssignHeapAllocation(inMethod, stmt, left, (NewArrayExpr) right, session);
        }
        else if(right instanceof NewMultiArrayExpr)
        {
            _writer.writeAssignNewMultiArrayExpr(inMethod, stmt, left, (NewMultiArrayExpr) right, session);
        }
        else if(right instanceof StringConstant)
        {
            _writer.writeAssignStringConstant(inMethod, stmt, left, (StringConstant) right, session);
        }
        else if(right instanceof ClassConstant)
        {
            _writer.writeAssignClassConstant(inMethod, stmt, left, (ClassConstant) right, session);
        }
        else if(right instanceof NumericConstant)
        {
            _writer.writeAssignNumConstant(inMethod, stmt, left, (NumericConstant) right, session);
        }
        else if(right instanceof NullConstant)
        {
            _writer.writeAssignNull(inMethod, stmt, left, session);
            // NoNullSupport: use the line below to remove Null Constants from the facts.
            // _writer.writeUnsupported(inMethod, stmt, session);
        }
        else if(right instanceof InstanceFieldRef)
        {
            InstanceFieldRef ref = (InstanceFieldRef) right;
            _writer.writeLoadInstanceField(inMethod, stmt, ref.getField(), (Local) ref.getBase(), left, session);
        }
        else if(right instanceof StaticFieldRef)
        {
            StaticFieldRef ref = (StaticFieldRef) right;
            _writer.writeLoadStaticField(inMethod, stmt, ref.getField(), left, session);
        }
        else if(right instanceof ArrayRef)
        {
            ArrayRef ref = (ArrayRef) right;
            Local base = (Local) ref.getBase();
            Value index = ref.getIndex();

            if(index instanceof Local || index instanceof IntConstant)
            {
                _writer.writeLoadArrayIndex(inMethod, stmt, base, left, session);
            }
            else
            {
                throw new RuntimeException("Cannot handle assignment: " + stmt + " (index: " + index.getClass() + ")");
            }
        }
        else if(right instanceof CastExpr)
        {
            CastExpr cast = (CastExpr) right;
            Value op = cast.getOp();

            if(op instanceof Local)
            {
                _writer.writeAssignCast(inMethod, stmt, left, (Local) op, cast.getCastType(), session);
            }
            else if(
                    op instanceof IntConstant
                            || op instanceof LongConstant
                            || op instanceof FloatConstant
                            || op instanceof DoubleConstant
                            || op instanceof NullConstant
                    )
            {
                // make sure we can jump to statement we do not care about (yet)
                _writer.writeUnsupported(inMethod, stmt, session);
            }
            else
            {
                throw new RuntimeException("Cannot handle assignment: " + stmt + " (op: " + op.getClass() + ")");
            }
        }
        else if(right instanceof PhiExpr)
        {
            for(Value alternative : ((PhiExpr) right).getValues())
            {
                _writer.writeAssignLocal(inMethod, stmt, left, (Local) alternative, session);
            }
        }
        else if(
                right instanceof BinopExpr
                        || right instanceof NegExpr
                        || right instanceof LengthExpr
                        || right instanceof InstanceOfExpr)
        {
            // make sure we can jump to statement we do not care about (yet)
            _writer.writeUnsupported(inMethod, stmt, session);
        }
        else
        {
            throw new RuntimeException("Cannot handle assignment: " + stmt + " (right: " + right.getClass() + ")");
        }
    }

    public void generateLeftNonLocal(SootMethod inMethod, AssignStmt stmt, Session session)
    {
        Value left = stmt.getLeftOp();
        Value right = stmt.getRightOp();

        // first make sure we have local variable for the right-hand-side.
        Local rightLocal = null;

        if(right instanceof Local)
        {
            rightLocal = (Local) right;
        }
        else if(right instanceof StringConstant)
        {
            rightLocal = _writer.writeStringConstantExpression(inMethod, stmt, (StringConstant) right, session);
        }
        else if(right instanceof NumericConstant)
        {
            rightLocal = _writer.writeNumConstantExpression(inMethod, stmt, (NumericConstant) right, session);
        }
        else if(right instanceof NullConstant)
        {
            rightLocal = _writer.writeNullExpression(inMethod, stmt, left.getType(), session);
            // NoNullSupport: use the line below to remove Null Constants from the facts.
            // _writer.writeUnsupported(inMethod, stmt, session);
        }
        else if(right instanceof ClassConstant)
        {
            rightLocal = _writer.writeClassConstantExpression(inMethod, stmt, (ClassConstant) right, session);
        }
        else
        {
            throw new RuntimeException("Cannot handle rhs: " + stmt + " (right: " + right.getClass() + ")");
        }

        // arrays
        //
        // NoNullSupport: use the line below to remove Null Constants from the facts.
        // if(left instanceof ArrayRef && rightLocal != null)
        if(left instanceof ArrayRef)
        {
            ArrayRef ref = (ArrayRef) left;
            Local base = (Local) ref.getBase();
            _writer.writeStoreArrayIndex(inMethod, stmt, base, rightLocal, session);
        }
        // NoNullSupport: use the line below to remove Null Constants from the facts.
        // else if(left instanceof InstanceFieldRef && rightLocal != null)
        else if(left instanceof InstanceFieldRef)
        {
            InstanceFieldRef ref = (InstanceFieldRef) left;
            _writer.writeStoreInstanceField(inMethod, stmt, ref.getField(), (Local) ref.getBase(), rightLocal, session);
        }
        // NoNullSupport: use the line below to remove Null Constants from the facts.
        // else if(left instanceof StaticFieldRef && rightLocal != null)
        else if(left instanceof StaticFieldRef)
        {
            StaticFieldRef ref = (StaticFieldRef) left;
            _writer.writeStoreStaticField(inMethod, stmt, ref.getField(), rightLocal, session);
        }
        // NoNullSupport: use the else part below to remove Null Constants from the facts.
        /*else if(right instanceof NullConstant)
        {
            _writer.writeUnsupported(inMethod, stmt, session);
            // skip, not relevant for pointer analysis
        }*/
        else
        {
            throw new RuntimeException("Cannot handle assignment: " + stmt + " (right: " + right.getClass() + ")");
        }
    }

    public void generate(SootMethod inMethod, IdentityStmt stmt, Session session)
    {
        Value left = stmt.getLeftOp();
        Value right = stmt.getRightOp();

        if(right instanceof CaughtExceptionRef) {
            // make sure we can jump to statement we do not care about (yet)
            _writer.writeUnsupported(inMethod, stmt, session);

            /* Handled by ExceptionHandler generation (ExceptionHandler:FormalParam).

               TODO Would be good to check more carefully that a caught
               exception does not occur anywhere else.
            */
            return;
        }
        else if(left instanceof Local && right instanceof ThisRef)
        {
            _writer.writeAssignLocal(inMethod, stmt, (Local) left, (ThisRef) right, session);
        }
        else if(left instanceof Local && right instanceof ParameterRef)
        {
            _writer.writeAssignLocal(inMethod, stmt, (Local) left, (ParameterRef) right, session);
        }
        else
        {
            throw new RuntimeException("Cannot handle identity statement: " + stmt);
        }
    }

    /**
     * Return statement
     */
    public void generate(SootMethod inMethod, ReturnStmt stmt, Session session)
    {
        Value v = stmt.getOp();

        if(v instanceof Local)
        {
            _writer.writeReturn(inMethod, stmt, (Local) v, session);
        }
        else if(v instanceof StringConstant)
        {
            Local tmp = _writer.writeStringConstantExpression(inMethod, stmt, (StringConstant) v, session);
            _writer.writeReturn(inMethod, stmt, tmp, session);
        }
        else if(v instanceof ClassConstant)
        {
            Local tmp = _writer.writeClassConstantExpression(inMethod, stmt, (ClassConstant) v, session);
            _writer.writeReturn(inMethod, stmt, tmp, session);
        }
        else if(v instanceof NumericConstant)
        {
            Local tmp = _writer.writeNumConstantExpression(inMethod, stmt, (NumericConstant) v, session);
            _writer.writeReturn(inMethod, stmt, tmp, session);
        }
        else if(v instanceof NullConstant)
        {
            Local tmp = _writer.writeNullExpression(inMethod, stmt, inMethod.getReturnType(), session);
            _writer.writeReturn(inMethod, stmt, tmp, session);
            // NoNullSupport: use the line below to remove Null Constants from the facts.
            // _writer.writeUnsupported(inMethod, stmt, session);
        }
        else
        {
            throw new RuntimeException("Unhandled return statement: " + stmt);
        }
    }

    public void generate(SootMethod inMethod, ThrowStmt stmt, Session session)
    {
        Value v = stmt.getOp();

        if(v instanceof Local)
        {
            _writer.writeThrow(inMethod, stmt, (Local) v, session);
        }
        else if(v instanceof NullConstant)
        {
            // make sure we can jump to statement we do not care about (yet)
            _writer.writeUnsupported(inMethod, stmt, session);
        }
        else
        {
            throw new RuntimeException("Unhandled throw statement: " + stmt);
        }
    }
}

