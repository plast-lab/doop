package org.clyze.doop.soot;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.clyze.doop.common.JavaRepresentation;
import org.clyze.doop.common.SessionCounter;
import org.clyze.persistent.model.jvm.JvmDynamicMethodInvocation;
import soot.*;
import soot.jimple.*;

class Representation extends JavaRepresentation {
    private final Map<SootMethod, String> _methodSigRepr = new ConcurrentHashMap<>();
    private final Map<SootField, String> _fieldSigRepr = new ConcurrentHashMap<>();
    private final Map<Trap, String> _trapRepr = new ConcurrentHashMap<>();
    private final Map<SootMethod, String> methodNames = new ConcurrentHashMap<>();

    static String classConstant(SootClass c) {
        return classConstant(c.getName());
    }

    static String classConstant(Type t) {
        return classConstant(t.toString());
    }

    String signature(SootMethod m) {
        String result = _methodSigRepr.get(m);
        if (result == null) {
            result = stripQuotes(m.getSignature());
            _methodSigRepr.put(m, result);
        }

        return result;
    }

    String signature(SootField f) {
        String result = _fieldSigRepr.get(f);
        if (result == null) {
            result = stripQuotes(f.getSignature());
            _fieldSigRepr.put(f, result);
        }
        return result;
    }

    static String signature(SootMethodRef mRef) {
        return stripQuotes(mRef.toString());
    }

    String simpleName(SootMethod m) {
        String result = methodNames.get(m);
        if (result == null) {
            result = stripQuotes(m.getName());
            methodNames.put(m, result);
        }
        return result;
    }

    public static String unescapeSimpleName(String n) {
        boolean escaped = n.startsWith("'") && n.endsWith("'");
        return escaped ? n.substring(1, n.length()-1) : n;
    }

    static String simpleName(SootMethodRef m) {
        return m.getName();
    }

    static String simpleName(SootField m) {
        return m.getName();
    }

    static String params(SootMethod m) {
        StringBuilder builder = new StringBuilder();
        int count = m.getParameterCount();
        for(int i = 0; i < count; i++) {
            builder.append(m.getParameterType(i));
            if (i != count - 1)
                builder.append(",");
        }
        return builder.toString();
    }

    String thisVar(SootMethod m) {
        return signature(m) + "/@this";
    }

    String nativeReturnVar(SootMethod m) {
        return nativeReturnVarOfMethod(signature(m));
    }

    String param(SootMethod m, int i) {
        return signature(m) + "/@parameter" + i;
    }

    String local(SootMethod m, Local l) {
        return stripQuotes(localId(signature(m), l.getName()));
    }

    String newLocalIntermediate(SootMethod m, Local l, SessionCounter counter) {
        return newLocalIntermediateId(local(m, l), counter);
    }

    String handler(SootMethod m, Trap trap, SessionCounter counter) {
        String result = _trapRepr.get(trap);

        if(result == null)
        {
            String name = handlerMid(trap.getException().getName());
            result = numberedInstructionId(signature(m), name, counter);
            _trapRepr.put(trap, result);
        }

        return result;
    }

    String throwLocal(SootMethod m, Local l, SessionCounter counter) {
        String name = throwLocalId(l.getName());
        return numberedInstructionId(signature(m), name, counter);
    }

    private String getKind(Stmt stmt) {
        String kind = "unknown";
        if (stmt instanceof AssignStmt) {
            if (((AssignStmt) stmt).getRightOp() instanceof CastExpr)
                kind = "assign-cast";
            else
                kind = "assign";
        } else if ((stmt instanceof AssignStmt) || (stmt instanceof IdentityStmt))
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

    String unsupported(SootMethod inMethod, Stmt stmt, int index) {
        return unsupportedId(signature(inMethod), getKind(stmt), stmt.toString(), index);
    }

    /**
     * Text representation of instruction to be used as refmode.
     */
    String instruction(SootMethod inMethod, Stmt stmt, int index) {
        return instructionId(signature(inMethod), getKind(stmt), index);
    }

    String invoke(SootMethod inMethod, InvokeExpr expr, SessionCounter counter) {
        SootMethodRef exprMethodRef = expr.getMethodRef();
        String name = simpleName(exprMethodRef);
        String midPart;
        if (expr instanceof DynamicInvokeExpr) {
            SootMethodRef bootRef = ((DynamicInvokeExpr)expr).getBootstrapMethodRef();
            String bootName = simpleName(bootRef);
            midPart = JvmDynamicMethodInvocation.genericId(bootName, name);
        } else
            midPart = exprMethodRef.getDeclaringClass() + "." + name;
        return numberedInstructionId(signature(inMethod), midPart, counter);
    }

    String heapAlloc(SootMethod inMethod, Value expr, SessionCounter counter) {
        if(expr instanceof NewExpr || expr instanceof NewArrayExpr)
            return heapAlloc(inMethod, expr.getType(), counter);
	    else if(expr instanceof NewMultiArrayExpr)
            return heapAlloc(inMethod, expr.getType(), counter);
            //      return signature(inMethod) + "/" + type + "/" +  session.nextNumber(type);
	    else
            throw new RuntimeException("Cannot handle new expression: " + expr);
    }

    String heapMultiArrayAlloc(SootMethod inMethod, /* NewMultiArrayExpr expr, */ ArrayType type, SessionCounter counter) {
        return heapAlloc(inMethod, type, counter);
    }

    private String heapAlloc(SootMethod inMethod, Type type, SessionCounter counter) {
        return heapAllocId(signature(inMethod), type.toString(), counter);
    }
}
