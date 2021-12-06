package org.clyze.doop.soot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.clyze.doop.common.InstrInfo;
import org.clyze.doop.common.Phantoms;
import org.clyze.doop.common.SessionCounter;
import soot.*;
import soot.jimple.*;
import soot.shimple.PhiExpr;
import soot.shimple.Shimple;

import static org.clyze.doop.common.JavaRepresentation.numberedInstructionId;

/**
 * Traverses Soot classes and invokes methods in FactWriter to
 * generate facts. The class FactGenerator is the main class
 * controlling what facts are generated.
 */

class FactGenerator implements Runnable {

    public static final AtomicInteger methodsWithoutActiveBodies = new AtomicInteger(0);

    private final FactWriter _writer;
    private final boolean _ssa;
    private final Set<SootClass> _sootClasses;
    private final Phantoms phantoms;
    private final SootParameters sootParameters;
    private final SootDriver _driver;

    FactGenerator(FactWriter writer, Set<SootClass> sootClasses, SootDriver driver, SootParameters sootParameters, Phantoms phantoms)
    {
        this._writer = writer;
        this._sootClasses = sootClasses;
        this.sootParameters = sootParameters;
        this._ssa = sootParameters._ssa;
        this.phantoms = phantoms;
        this._driver = driver;
    }

    @Override
    public void run() {
        final boolean ignoreErrors = _driver._ignoreFactGenErrors;

        if (!ignoreErrors && _driver.errorsExist())
            return;

        for (SootClass _sootClass : _sootClasses) {
            _writer.writeClassOrInterfaceType(_sootClass);

            for (String mod : getModifiers(_sootClass.getModifiers(), false))
                if (!mod.trim().equals(""))
                    _writer.writeClassModifier(_sootClass, mod);

            // the isInterface condition prevents Object as superclass of interface
            if (_sootClass.hasSuperclass() && !_sootClass.isInterface()) {
                _writer.writeDirectSuperclass(_sootClass, _sootClass.getSuperclass());
            }

            for (SootClass i : _sootClass.getInterfaces()) {
                _writer.writeDirectSuperinterface(_sootClass, i);
            }

            _sootClass.getFields().forEach(this::generate);

            for (SootMethod m : new ArrayList<>(_sootClass.getMethods())) {
                SessionCounter session = new SessionCounter();
                try {
                    generate(m, session);
                } catch (Throwable t) {
                    // Map<Thread,StackTraceElement[]> liveThreads = Thread.getAllStackTraces();
                    // for (Iterator<Thread> i = liveThreads.keySet().iterator(); i.hasNext(); ) {
                    //     Thread key = i.next();
                    //     System.err.println("Thread " + key.getName());
                    //     StackTraceElement[] trace = liveThreads.getLibrary(key);
                    //     for (int j = 0; j < trace.length; j++) {
                    //         System.err.println("\tat " + trace[j]);
                    //     }
                    // }
                    String msg = "Error while processing method: " + m + ": " + t.getMessage();
                    System.err.println(msg);
                    if (!ignoreErrors) {
                        // Inform the driver. This is safer than throwing an
                        // exception, since it could be lost due to the executor
                        // service running this class.
                        _driver.markError();
                        return;
                    }
                }
            }
        }
    }

    private void generate(SootField f)
    {
        _writer.writeField(f);
        _writer.writeFieldInitialValue(f);

        for (String m : getModifiers(f.getModifiers(), false))
            _writer.writeFieldModifier(f, m);
    }

    /**
     * Given a JVM representation of the modifiers of a method/field/property,
     * return a representation of String tokens.
     *
     * @param modifiers    the modifiers integer
     * @param isMethod     "true" if the modifiers concern a method, "false" otherwise
     */
    private static Collection<String> getModifiers(int modifiers, boolean isMethod) {
        // Take the modifiers from Soot, so that we are robust against
        // changes of the JVM spec.
        String[] modifierStrings = Modifier.toString(modifiers).split(" ");
        // Fix modifiers that mean different things for methods.
        if (isMethod)
            for (int i = 0; i < modifierStrings.length; i++)
                if ("transient".equals(modifierStrings[i]))
                    modifierStrings[i] = "varargs";
                else if ("volatile".equals(modifierStrings[i]))
                    modifierStrings[i] = "bridge";
        // Handle modifiers that are not in the Modifier.toString() output.
        Collection<String> ret = new ArrayList<>(Arrays.asList(modifierStrings));
        if(Modifier.isSynthetic(modifiers))
            ret.add("synthetic");
        if(Modifier.isConstructor(modifiers))
            ret.add("constructor");
        if(Modifier.isDeclaredSynchronized(modifiers))
            ret.add("declared-synchronized");
        return ret;
    }

    /* Check if a Type refers to a phantom class */
    private boolean isPhantom(Type t) {
        boolean isPhantom = false;
        if (t instanceof RefLikeType) {
            if (t instanceof RefType)
                isPhantom = ((RefType) t).getSootClass().isPhantom();
            else if (t instanceof ArrayType)
                isPhantom = isPhantom(((ArrayType) t).getElementType());
        }
        if (isPhantom)
            _writer.writePhantomType(t);
        return isPhantom;
    }

    /* Check for phantom classes in a method signature. */
    private boolean isPhantomBased(SootMethod m) {
        for (SootClass clazz: m.getExceptions())
            if (isPhantom(clazz.getType())) {
                phantoms.reportPhantom("Exception", clazz.getName());
                return true;
            }

        for (int i = 0 ; i < m.getParameterCount(); i++)
            if(isPhantom(m.getParameterType(i))) {
                phantoms.reportPhantomSigType("Parameter type", m.getParameterType(i).toString(), m.getSignature());
                return true;
            }

        if (isPhantom(m.getReturnType())) {
            phantoms.reportPhantomSigType("Return type", m.getReturnType().toString(), m.getSignature());
            return true;
        }

        return false;
    }

    void generate(SootMethod m, SessionCounter session) {
        String methodId = _writer.writeMethod(m);

        if (m.isPhantom()) {
            _writer.writePhantomMethod(methodId);
            return;
        }

        if (isPhantomBased(m))
            _writer.writePhantomBasedMethod(methodId);

        for (String mod : getModifiers(m.getModifiers(), true))
            _writer.writeMethodModifier(methodId, mod);

        if (!m.isStatic())
            _writer.writeThisVar(methodId, m.getDeclaringClass());

        if (m.isNative()) {
            _writer.writeNativeMethodId(methodId, m.getDeclaringClass().toString(), _writer._rep.simpleName(m));
            _writer.writeNativeReturnVar(methodId, m.getReturnType());
        }

        for(int i = 0 ; i < m.getParameterCount(); i++)
            _writer.writeFormalParam(methodId, m.getParameterType(i), i);

        for(SootClass clazz: m.getExceptions())
            _writer.writeMethodDeclaresException(m, clazz);

        if(!(m.isAbstract() || m.isNative())) {
            if(!m.hasActiveBody()) {
                // This instruction is the bottleneck of
                // soot-fact-generation.
                // synchronized(Scene.v()) {
                m.retrieveActiveBody();
                // } // synchronizing so broadly = giving up on Soot's races

                // System.err.println("Found method without active body: " + m.getSignature());
                methodsWithoutActiveBodies.incrementAndGet();
            }

            Body b0 = m.getActiveBody();
            try {
                if (b0 != null) {
                    Body b = b0;
                    if (_ssa) {
                        b = Shimple.v().newBody(b);
                        m.setActiveBody(b);
                    }
                    DoopRenamer.transform(b);
                    generate(m, b, session);
                    // If the Shimple body is not needed anymore, put
                    // back original body. This saves some memory.
                    if (sootParameters._lowMem && !sootParameters._generateJimple)
                        m.setActiveBody(b0);
                }
            } catch (RuntimeException ex) {
                System.err.println("Fact generation failed for method " + m.getSignature() + ".");
                ex.printStackTrace();
                throw ex;
            }
        }
    }

    /**
     * Check if a unit (instruction) is going to be transformed to a set of other
     * instructions that will replace it. Used to detect instruction index disruptions.
     *
     * @param u   the instruction to check
     * @return    true if the instruction will be transformed
     */
    private static boolean isTransformedAway(Unit u) {
        // Phi assignments are currently the only pattern captured.
        return (u instanceof AssignStmt && ((AssignStmt)u).getRightOp() instanceof PhiExpr);
    }

    private void generate(SootMethod m, Body b, SessionCounter session) {
        String methodId = _writer._rep.signature(m);
        for(Local l : b.getLocals())
            _writer.writeLocal(methodId, l);

        IrrelevantStmtSwitch sw = new IrrelevantStmtSwitch();
        Map<Unit, InstrInfo> iis = new ConcurrentHashMap<>();
        for (Unit u : b.getUnits()) {
            u.apply(sw);

            String insn = numberedInstructionId(methodId, Representation.getKind(u), session);
            InstrInfo ii = null;
            // Skip instruction index numbering for phi assignments, so that no holes
            // (..., i, i+2,...) appear in instruction indices (issue #48).
            if (!isTransformedAway(u)) {
                ii = new InstrInfo(_writer.methodSig(m, null), insn, session.calcInstructionIndex(u));
                iis.put(u, ii);
            }
            if (sw.relevant) {
                if (u instanceof AssignStmt) {
                    generate(m, (AssignStmt) u, ii, session);
                } else if (u instanceof IdentityStmt) {
                    generate((IdentityStmt) u, ii, session);
                } else if (u instanceof InvokeStmt) {
                    _writer.writeInvoke(m, (InvokeStmt) u, ii, session);
                } else if (u instanceof ReturnStmt) {
                    generate((ReturnStmt) u, m.getReturnType(), ii, session);
                } else if (u instanceof ReturnVoidStmt) {
                    _writer.writeReturnVoid(ii);
                } else if (u instanceof ThrowStmt) {
                    generate(methodId, (ThrowStmt) u, ii, session);
                } else if ((u instanceof GotoStmt) || (u instanceof IfStmt) ||
                           (u instanceof SwitchStmt) || (u instanceof NopStmt)) {
                    // processed in second run: we might not know the number of
                    // the unit yet.
                } else if (u instanceof EnterMonitorStmt) {
                    //TODO: how to handle EnterMonitorStmt when op is not a Local?
                    EnterMonitorStmt stmt = (EnterMonitorStmt) u;
                    if (stmt.getOp() instanceof Local)
                        _writer.writeEnterMonitor(ii, (Local) stmt.getOp());
                } else if (u instanceof ExitMonitorStmt) {
                    //TODO: how to handle ExitMonitorStmt when op is not a Local?
                    ExitMonitorStmt stmt = (ExitMonitorStmt) u;
                    if (stmt.getOp() instanceof Local)
                        _writer.writeExitMonitor(ii, (Local) stmt.getOp());
                } else {
                    throw new RuntimeException("Cannot handle statement: " + u);
                }
            } else {
                // only reason for assign or invoke statements to be irrelevant
                // is the invocation of a method on a phantom class
                if (u instanceof AssignStmt) {
                    generate(m, (AssignStmt) u, ii, session);
                    _writer.writeAssignPhantomInvoke(ii);
                    generatePhantom(sw.cause);
                } else if (u instanceof InvokeStmt) {
                    // record invocation and calculate PhantomInvoke via logic
                    _writer.writeInvoke(m, (InvokeStmt) u, ii, session);
                    generatePhantom(sw.cause);
                } else if (u instanceof BreakpointStmt)
                    _writer.writeBreakpointStmt(ii);
                else
                    throw new RuntimeException("Unexpected irrelevant statement: " + u);
            }
        }

        for(Unit u : b.getUnits()) {
            if (u instanceof GotoStmt) {
                _writer.writeGoto((GotoStmt)u, iis.get(u), session);
            } else if (u instanceof IfStmt) {
                IfStmt ifStmt = (IfStmt)u;
                _writer.writeWithPossiblePhiTarget(ifStmt.getTarget(), session, (indexTo -> _writer.writeIf(ifStmt, iis.get(u), indexTo)));
            } else if (u instanceof TableSwitchStmt) {
                _writer.writeTableSwitch((TableSwitchStmt) u, iis.get(u), session);
            } else if (u instanceof LookupSwitchStmt) {
                _writer.writeLookupSwitch((LookupSwitchStmt) u, iis.get(u), session);
            } else if (!(u instanceof Stmt)) {
                throw new RuntimeException("Not a statement: " + u);
            }
        }

        Trap previous = null;
        for (Trap t : b.getTraps()) {
            _writer.writeExceptionHandler(methodId, t, session);
            if (previous != null)
                _writer.writeExceptionHandlerPrevious(methodId, t, previous, session);
            previous = t;
        }
    }

    private void generatePhantom(Object cause) {
        if (_writer.checkAndRegisterPhantom(cause))
            return;

        if (cause instanceof SootClass)
            _writer.writePhantomType(((SootClass)cause).getType());
        else if (cause instanceof SootMethod)
            _writer.writePhantomMethod(_writer._rep.signature((SootMethod)cause));
        else
            System.err.println("Ignoring phantom cause: " + cause);
    }

    /**
     * Assignment statement
     */
    private void generate(SootMethod inMethod, AssignStmt stmt, InstrInfo ii, SessionCounter session) {
        if (stmt.getLeftOp() instanceof Local)
            generateAssignToLocal(inMethod, stmt, ii, session);
        else
            generateAssignToNonLocal(stmt, ii, session);
    }

    private void generateAssignToLocal(SootMethod inMethod, AssignStmt stmt, InstrInfo ii, SessionCounter session) {
        Local left = (Local) stmt.getLeftOp();
        Value right = stmt.getRightOp();

        if (right instanceof Local)
            _writer.writeAssignLocal(ii, left, (Local) right);
        else if (right instanceof InvokeExpr)
            _writer.writeAssignInvoke(inMethod, stmt, ii, left, session);
        else if (right instanceof NewExpr)
            _writer.writeAssignHeapAllocation(stmt, ii, left, right, session);
        else if (right instanceof NewArrayExpr)
            _writer.writeAssignHeapAllocation(stmt, ii, left, right, session);
        else if (right instanceof NewMultiArrayExpr)
            _writer.writeAssignNewMultiArrayExpr(stmt, ii, left, (NewMultiArrayExpr) right, session);
        else if (right instanceof StringConstant)
            _writer.writeAssignStringConstant(stmt, ii, left, (StringConstant) right);
        else if (right instanceof ClassConstant)
            _writer.writeAssignClassConstant(ii, left, (ClassConstant) right);
        else if (right instanceof NumericConstant)
            _writer.writeAssignNumConstant(ii, left, (NumericConstant) right);
        else if (right instanceof NullConstant) {
            _writer.writeAssignNull(ii, left);
            // NoNullSupport: use the line below to remove Null Constants from the facts.
            // _writer.writeUnsupported(inMethod, stmt, session);
        } else if (right instanceof InstanceFieldRef) {
            InstanceFieldRef ref = (InstanceFieldRef) right;
            _writer.writeLoadInstanceField(ii, ref.getField(), (Local) ref.getBase(), left);
        } else if (right instanceof StaticFieldRef) {
            StaticFieldRef ref = (StaticFieldRef) right;
            _writer.writeLoadStaticField(ii, ref.getField(), left);
        } else if (right instanceof ArrayRef) {
            ArrayRef ref = (ArrayRef) right;
            Local base = (Local) ref.getBase();
            Value index = ref.getIndex();
            _writer.writeLoadArrayIndex(stmt, ii, base, left, index);
        } else if (right instanceof CastExpr) {
            CastExpr cast = (CastExpr) right;
            Value op = cast.getOp();

            if (op instanceof Local)
                _writer.writeAssignCast(ii, left, (Local) op, cast.getCastType());
            else if (op instanceof NumericConstant) {
                // seems to always get optimized out, do we need this?
                _writer.writeAssignCastNumericConstant(ii, left, (NumericConstant) op, cast.getCastType());
            } else if (op instanceof NullConstant || op instanceof ClassConstant || op instanceof StringConstant)
                _writer.writeAssignCastNull(ii, left, cast.getCastType());
            else
                throw new RuntimeException("Cannot handle assignment: " + stmt + " (op: " + op.getClass() + ")");
        } else if (right instanceof PhiExpr) {
            _writer.writePhiAssign(_writer.methodSig(inMethod, null), stmt, left, (PhiExpr) right, session);
        } else if (right instanceof BinopExpr)
            _writer.writeAssignBinop(ii, left, (BinopExpr) right);
        else if (right instanceof UnopExpr)
            _writer.writeAssignUnop(ii, left, (UnopExpr) right);
        else if (right instanceof InstanceOfExpr) {
            InstanceOfExpr expr = (InstanceOfExpr) right;
            if (expr.getOp() instanceof Local)
                _writer.writeAssignInstanceOf(ii, left, (Local) expr.getOp(), expr.getCheckType());
            else // TODO check if this is possible (instanceof on something that is not a local var)
                _writer.writeUnsupported(stmt, ii, session);
        } else
            throw new RuntimeException("Cannot handle assignment: " + stmt + " (right: " + right.getClass() + ")");
    }

    private void generateAssignToNonLocal(AssignStmt stmt, InstrInfo ii, SessionCounter session) {
        Value left = stmt.getLeftOp();
        Value right = stmt.getRightOp();

        // first make sure we have local variable for the right-hand-side.
        Local rightLocal;

        if (right instanceof Local)
            rightLocal = (Local) right;
        else if (right instanceof StringConstant)
            rightLocal = _writer.writeStringConstantExpression(stmt, ii.methodId, (StringConstant) right, session);
        else if (right instanceof NumericConstant)
            rightLocal = _writer.writeNumConstantExpression(ii.methodId, (NumericConstant) right, left.getType(), session);
        else // NoNullSupport: use the line below to remove Null Constants from the facts.
            // _writer.writeUnsupported(inMethod, stmt, session);
            if (right instanceof NullConstant)
                rightLocal = _writer.writeNullExpression(ii.methodId, left.getType(), session);
        else if (right instanceof ClassConstant)
            rightLocal = _writer.writeClassConstantExpression(ii.methodId, (ClassConstant) right, session);
        else if (right instanceof MethodHandle)
            rightLocal = _writer.writeMethodHandleConstantExpression(ii.methodId, (MethodHandle) right, session);
        else
            throw new RuntimeException("Cannot handle rhs: " + stmt + " (right: " + right.getClass() + ")");

        // arrays
        //
        // NoNullSupport: use the line below to remove Null Constants from the facts.
        // if(left instanceof ArrayRef && rightLocal != null)
        if (left instanceof ArrayRef) {
            ArrayRef ref = (ArrayRef) left;
            Local base = (Local) ref.getBase();
            Value index = ref.getIndex();
            _writer.writeStoreArrayIndex(stmt, ii, base, rightLocal, index);
        }
        // NoNullSupport: use the line below to remove Null Constants from the facts.
        // else if(left instanceof InstanceFieldRef && rightLocal != null)
        else if(left instanceof InstanceFieldRef) {
            InstanceFieldRef ref = (InstanceFieldRef) left;
            _writer.writeStoreInstanceField(ii, ref.getField(), (Local) ref.getBase(), rightLocal);
        }
        // NoNullSupport: use the line below to remove Null Constants from the facts.
        // else if(left instanceof StaticFieldRef && rightLocal != null)
        else if(left instanceof StaticFieldRef) {
            StaticFieldRef ref = (StaticFieldRef) left;
            _writer.writeStoreStaticField(ii, ref.getField(), rightLocal);
        }
        // NoNullSupport: use the else part below to remove Null Constants from the facts.
        /*else if(right instanceof NullConstant)
        {
            _writer.writeUnsupported(inMethod, stmt, session);
            // skip, not relevant for pointer analysis
        }*/
        else
            throw new RuntimeException("Cannot handle assignment: " + stmt + " (right: " + right.getClass() + ")");
    }

    private void generate(IdentityStmt stmt, InstrInfo ii, SessionCounter session)
    {
        Value left = stmt.getLeftOp();
        Value right = stmt.getRightOp();

        if(right instanceof CaughtExceptionRef) {
            // make sure we can jump to statement we do not care about (yet)
            _writer.writeUnsupported(stmt, ii, session);

            /* Handled by ExceptionHandler generation (ExceptionHandler:FormalParam).

               TODO Would be good to check more carefully that a caught
               exception does not occur anywhere else.
            */
        }
        else if(left instanceof Local && right instanceof ThisRef)
            _writer.writeAssignThisToLocal(ii, (Local) left);
        else if(left instanceof Local && right instanceof ParameterRef)
            _writer.writeAssignLocal(ii, (Local) left, (ParameterRef) right);
        else
            throw new RuntimeException("Cannot handle identity statement: " + stmt);
    }

    /**
     * Return statement
     */
    private void generate(ReturnStmt stmt, Type returnType, InstrInfo ii, SessionCounter session)
    {
        Value v = stmt.getOp();

        if (v instanceof Local)
            _writer.writeReturn(ii, (Local) v);
        else if (v instanceof StringConstant) {
            Local tmp = _writer.writeStringConstantExpression(stmt, ii.methodId, (StringConstant) v, session);
            _writer.writeReturn(ii, tmp);
        }
        else if (v instanceof ClassConstant) {
            Local tmp = _writer.writeClassConstantExpression(ii.methodId, (ClassConstant) v, session);
            _writer.writeReturn(ii, tmp);
        }
        else if (v instanceof NumericConstant) {
            Local tmp = _writer.writeNumConstantExpression(ii.methodId, (NumericConstant) v, returnType, session);
            _writer.writeReturn(ii, tmp);
        } else if(v instanceof MethodHandle) {
            Local tmp = _writer.writeMethodHandleConstantExpression(ii.methodId, (MethodHandle) v, session);
            _writer.writeReturn(ii, tmp);
        } else if (v instanceof NullConstant) {
            Local tmp = _writer.writeNullExpression(ii.methodId, returnType, session);
            _writer.writeReturn(ii, tmp);
            // NoNullSupport: use the line below to remove Null Constants from the facts.
            // _writer.writeUnsupported(inMethod, stmt, session);
        } else
            throw new RuntimeException("Unhandled return statement: " + stmt);
    }

    private void generate(String inMethod, ThrowStmt stmt, InstrInfo ii, SessionCounter session) {
        Value v = stmt.getOp();

        if (v instanceof Local)
            _writer.writeThrow(inMethod, stmt, (Local) v, session);
        else if (v instanceof NullConstant)
            _writer.writeThrowNull(ii);
        else
            throw new RuntimeException("Unhandled throw statement: " + stmt);
    }
}

