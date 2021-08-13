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

    static String simpleName(SootMethodInterface m) {
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

    String thisVar(String methodId) {
        return methodId + "/@this";
    }

    String nativeReturnVar(String methodId) {
        return nativeReturnVarOfMethod(methodId);
    }

    String param(String methodId, int i) {
        return methodId + "/@parameter" + i;
    }

    String local(String m, Local l) {
        return stripQuotes(localId(m, l.getName()));
    }

    String newLocalIntermediate(String m, Local l, SessionCounter counter) {
        return newLocalIntermediateId(local(m, l), counter);
    }

    String handler(String methodId, Trap trap, SessionCounter counter) {
        String result = _trapRepr.get(trap);

        if(result == null)
        {
            String name = handlerMid(trap.getException().getName());
            result = numberedInstructionId(methodId, name, counter);
            _trapRepr.put(trap, result);
        }

        return result;
    }

    String throwLocal(String methodId, Local l, SessionCounter counter) {
        String name = throwLocalId(l.getName());
        return numberedInstructionId(methodId, name, counter);
    }

    public static String getKind(Unit unit) {
        if (unit instanceof AssignStmt) {
            AssignStmt assignStmt = (AssignStmt) unit;
            Value rightOp = assignStmt.getRightOp();
            Value leftOp = assignStmt.getLeftOp();
            if (rightOp instanceof CastExpr)
                return "assign-cast";
            else if (rightOp instanceof FieldRef)
                return "read-field-" + ((FieldRef) rightOp).getFieldRef().name();
            else if (leftOp instanceof FieldRef)
                return "write-field-" + ((FieldRef) leftOp).getFieldRef().name();
            else if (rightOp instanceof ArrayRef)
                return "read-array-idx";
            else if (leftOp instanceof ArrayRef)
                return "write-array-idx";
            else
                return "assign";
        } else if (unit instanceof IdentityStmt)
            return "assign";
        else if (unit instanceof DefinitionStmt)
            return "definition";
        else if (unit instanceof EnterMonitorStmt)
            return "enter-monitor";
        else if (unit instanceof ExitMonitorStmt)
            return "exit-monitor";
        else if (unit instanceof GotoStmt)
            return "goto";
        else if (unit instanceof IfStmt)
            return "if";
        else if (unit instanceof InvokeStmt)
            return "invoke";
        else if (unit instanceof RetStmt)
            return "ret";
        else if (unit instanceof ReturnVoidStmt)
            return "return-void";
        else if (unit instanceof ReturnStmt)
            return "return";
        else if (unit instanceof SwitchStmt) {
            if (unit instanceof TableSwitchStmt)
                return "table-switch";
            else if (unit instanceof LookupSwitchStmt)
                return "lookup-switch";
            else
                return "switch";
        } else if (unit instanceof ThrowStmt)
            return "throw";
        return "unknown";
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

    String heapAlloc(String inMethod, Value expr, SessionCounter counter) {
        if(expr instanceof NewExpr || expr instanceof NewArrayExpr)
            return heapAlloc(inMethod, expr.getType(), counter);
	    else if(expr instanceof NewMultiArrayExpr)
            return heapAlloc(inMethod, expr.getType(), counter);
            //      return signature(inMethod) + "/" + type + "/" +  session.nextNumber(type);
	    else
            throw new RuntimeException("Cannot handle new expression: " + expr);
    }

    String heapMultiArrayAlloc(String inMethod, /* NewMultiArrayExpr expr, */ ArrayType type, SessionCounter counter) {
        return heapAlloc(inMethod, type, counter);
    }

    private String heapAlloc(String inMethod, Type type, SessionCounter counter) {
        return heapAllocId(inMethod, type.toString(), counter);
    }
}
