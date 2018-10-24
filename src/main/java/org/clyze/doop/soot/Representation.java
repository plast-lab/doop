package org.clyze.doop.soot;

import org.clyze.doop.common.JavaRepresentation;
import org.clyze.doop.common.SessionCounter;
import org.clyze.persistent.model.doop.DynamicMethodInvocation;
import soot.*;
import soot.jimple.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class Representation extends JavaRepresentation {
    private final Map<SootMethod, String> _methodSigRepr = new ConcurrentHashMap<>();
    private final Map<Trap, String> _trapRepr = new ConcurrentHashMap<>();
    private static final List<String> jimpleKeywordList = Jimple.jimpleKeywordList();
    private final Map<SootMethod, String> methodNames = new ConcurrentHashMap<>();

    static String classConstant(SootClass c) {
        return classConstant(c.getName());
    }

    static String classConstant(Type t) {
        return classConstant(t.toString());
    }

    String signature(SootMethod m) {
        String result = _methodSigRepr.get(m);

        if(result == null)
        {
            result = m.getSignature();
            _methodSigRepr.put(m, result);
        }

        return result;
    }

    static String signature(SootField f) {
        return f.getSignature();
    }

    String simpleName(SootMethod m) {
        String result = methodNames.get(m);
        if (result == null) {
            result = escapeSimpleName(m.getName());
            methodNames.put(m, result);
        }
        return result;
    }

    // Fix simple name if it is a special Jimple keyword.
    private static String escapeSimpleName(String n) {
        boolean escape = (!n.startsWith("'") && jimpleKeywordList.contains(n));
        return escape ? "'"+n+"'" : n;
    }

    private static String simpleName(SootMethodRef m) {
        return escapeSimpleName(m.name());
    }

    static String simpleName(SootField m) {
        return m.getName();
    }

    static String params(SootMethod m) {
        StringBuilder builder = new StringBuilder();
        int count = m.getParameterCount();
        builder.append("(");
        for(int i = 0; i < count; i++) {
            builder.append(m.getParameterType(i));
            if (i != count - 1)
                builder.append(",");
        }
        builder.append(")");
        return builder.toString();
    }

    static String thisVar(SootMethod m) {
        return getMethodSignature(m) + "/@this";
    }

    static String nativeReturnVar(SootMethod m) {
        return nativeReturnVarOfMethod(getMethodSignature(m));
    }

    static String param(SootMethod m, int i) {
        return getMethodSignature(m) + "/@parameter" + i;
    }

    static String local(SootMethod m, Local l) {
        return localId(getMethodSignature(m), l.getName());
    }

    static String newLocalIntermediate(SootMethod m, Local l, SessionCounter counter) {
        return newLocalIntermediateId(local(m, l), counter);
    }

    String handler(SootMethod m, Trap trap, SessionCounter counter) {
        String result = _trapRepr.get(trap);

        if(result == null)
        {
            String name = handlerMid(trap.getException().getName());
            result = numberedInstructionId(getMethodSignature(m), name, counter);
            _trapRepr.put(trap, result);
        }

        return result;
    }

    static String throwLocal(SootMethod m, Local l, SessionCounter counter) {
        String name = throwLocalId(l.getName());
        return numberedInstructionId(getMethodSignature(m), name, counter);
    }

    private static String getMethodSignature(SootMethod m) {
        return m.getSignature();
    }

    private static String getKind(Stmt stmt) {
        String kind = "unknown";
        if ((stmt instanceof AssignStmt) || (stmt instanceof IdentityStmt))
            kind = "assign";
        else if(stmt instanceof DefinitionStmt)
            kind = "definition";
        else if(stmt instanceof EnterMonitorStmt)
            kind = "enter-monitor";
        else if(stmt instanceof ExitMonitorStmt)
            kind = "exit-monitor";
        else if(stmt instanceof GotoStmt)
            kind = "goto";
        else if(stmt instanceof IfStmt)
            kind = "if";
        else if(stmt instanceof InvokeStmt)
            kind = "invoke";
        else if(stmt instanceof RetStmt)
            kind = "ret";
        else if(stmt instanceof ReturnVoidStmt)
            kind = "return-void";
        else if(stmt instanceof ReturnStmt)
            kind = "return";
        else if(stmt instanceof SwitchStmt)
            kind = "switch";
        else if(stmt instanceof ThrowStmt)
            kind = "throw";
        return kind;
    }

    static String unsupported(SootMethod inMethod, Stmt stmt, int index) {
        return unsupportedId(getMethodSignature(inMethod), getKind(stmt), stmt.toString(), index);
    }

    /**
     * Text representation of instruction to be used as refmode.
     */
    static String instruction(SootMethod inMethod, Stmt stmt, int index) {
        return instructionId(getMethodSignature(inMethod), getKind(stmt), index);
    }

    static String invoke(SootMethod inMethod, InvokeExpr expr, SessionCounter counter) {
        String midPart = (expr instanceof DynamicInvokeExpr) ?
            dynamicInvokeIdMiddle((DynamicInvokeExpr)expr) : invokeIdMiddle(expr);
        return numberedInstructionId(getMethodSignature(inMethod), midPart, counter);
    }

    private static String invokeIdMiddle(InvokeExpr expr) {
        SootMethodRef exprMethodRef = expr.getMethodRef();
        return exprMethodRef.declaringClass() + "." + simpleName(exprMethodRef);
    }

    // Create a middle part for invokedynamic ids. It currently
    // supports the LambdaMetafactory machinery, returning a default
    // value for other (or missing) bootstrap methods.
    private static String dynamicInvokeIdMiddle(DynamicInvokeExpr expr) {
        // The signatures of the two lambda metafactories we currently support.
        final String DEFAULT_L_METAFACTORY = "<java.lang.invoke.LambdaMetafactory: java.lang.invoke.CallSite metafactory(java.lang.invoke.MethodHandles$Lookup,java.lang.String,java.lang.invoke.MethodType,java.lang.invoke.MethodType,java.lang.invoke.MethodHandle,java.lang.invoke.MethodType)>";
        final String ALT_L_METAFACTORY = "<java.lang.invoke.LambdaMetafactory: java.lang.invoke.CallSite altMetafactory(java.lang.invoke.MethodHandles$Lookup,java.lang.String,java.lang.invoke.MethodType,java.lang.Object[])>";

        SootMethodRef bootMethRef = expr.getBootstrapMethodRef();
        if (bootMethRef != null) {
            String bootMethName = bootMethRef.resolve().toString();
            int bootArity = expr.getBootstrapArgCount();
            if (bootArity > 1) {
                Value val1 = expr.getBootstrapArg(1);
                if ((val1 instanceof MethodHandle) &&
                    ((bootMethName.equals(DEFAULT_L_METAFACTORY)) ||
                     (bootMethName.equals(ALT_L_METAFACTORY)))) {
                    SootMethodRef smr = ((MethodHandle)val1).getMethodRef();
                    return DynamicMethodInvocation.genId(smr.declaringClass().toString(),
                            smr.name());
                }
                else
                    System.out.println("Representation: Unsupported invokedynamic, unknown boot method " + bootMethName + ", arity=" + bootArity);
            }
            else
                System.out.println("Representation: Unsupported invokedynamic (unknown boot method of arity 0)");
        }
        else
            System.out.println("Representation: Malformed invokedynamic (null bootmethod)");
        return invokeIdMiddle(expr);
    }


    static String heapAlloc(SootMethod inMethod, AnyNewExpr expr, SessionCounter counter) {
        if(expr instanceof NewExpr || expr instanceof NewArrayExpr)
            return heapAlloc(inMethod, expr.getType(), counter);
	    else if(expr instanceof NewMultiArrayExpr)
            return heapAlloc(inMethod, expr.getType(), counter);
            //      return getMethodSignature(inMethod) + "/" + type + "/" +  session.nextNumber(type);
	    else
            throw new RuntimeException("Cannot handle new expression: " + expr);
    }

    static String heapMultiArrayAlloc(SootMethod inMethod, /* NewMultiArrayExpr expr, */ ArrayType type, SessionCounter counter) {
        return heapAlloc(inMethod, type, counter);
    }

    static private String heapAlloc(SootMethod inMethod, Type type, SessionCounter counter) {
        return heapAllocId(getMethodSignature(inMethod), type.toString(), counter);
    }
}
