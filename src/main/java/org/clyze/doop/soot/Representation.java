package org.clyze.doop.soot;

import org.clyze.persistent.model.doop.DynamicMethodInvocation;
import soot.*;
import soot.jimple.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Representation {
    private Map<SootMethod, String> _methodRepr = new ConcurrentHashMap<>();
    private Map<SootMethod, String> _methodSigRepr = new ConcurrentHashMap<>();
    private Map<SootMethodRef, String> _methodRefSigRepr = new ConcurrentHashMap<>();
    private Map<Trap, String> _trapRepr = new ConcurrentHashMap<>();

    // Make it a trivial singleton.
    private static Representation _repr;
    private Representation() {}

    public static Representation getRepresentation() {
        if (_repr == null)
            _repr = new Representation();
        return _repr;
    }

    public String classConstant(SootClass c) {
        return "<class " + c.getName() + ">";
    }

    String classConstant(String className) {
        return "<class " + className + ">";
    }

    String classConstant(Type t) {
        return "<class " + t + ">";
    }

    public String signature(SootMethod m) {
        String result = _methodSigRepr.get(m);

        if(result == null)
        {
            result = m.getSignature();
            _methodSigRepr.put(m, result);
        }

        return result;
    }

    String signature(SootField f) {
        return f.getSignature();
    }

    String simpleName(SootMethod m) {
        return m.getName();
    }

    String simpleName(SootField m) {
        return m.getName();
    }

    String descriptor(SootMethod m)
    {
        StringBuilder builder = new StringBuilder();

        builder.append(m.getReturnType().toString());
        builder.append("(");
        for(int i = 0; i < m.getParameterCount(); i++)
        {
            builder.append(m.getParameterType(i));

            if(i != m.getParameterCount() - 1)
            {
                builder.append(",");
            }
        }
        builder.append(")");

        return builder.toString();
    }

    String thisVar(SootMethod m)
    {
        return getMethodSignature(m) + "/@this";
    }

    String nativeReturnVar(SootMethod m)
    {
        return getMethodSignature(m) + "/@native-return";
    }

    String param(SootMethod m, int i)
    {
        return getMethodSignature(m) + "/@parameter" + i;
    }

    String local(SootMethod m, Local l)
    {
        return getMethodSignature(m) + "/" + l.getName();
    }

    String newLocalIntermediate(SootMethod m, Local l, Session session)
    {
        String s = local(m, l);
        return s + "/intermediate/" +  session.nextNumber(s);
    }

    String handler(SootMethod m, Trap trap, Session session)
    {
        String result = _trapRepr.get(trap);

        if(result == null)
        {
            String name = "catch " + trap.getException().getName();
            result = getMethodSignature(m) + "/" + name + "/" + session.nextNumber(name);

            _trapRepr.put(trap, result);
        }

        return result;
    }

    String throwLocal(SootMethod m, Local l, Session session)
    {
        String name = "throw " + l.getName();
        return getMethodSignature(m) + "/" + name + "/" + session.nextNumber(name);
    }

    private String getMethodSignature(SootMethod m)
    {
        return m.getSignature();
    }

    private String getKind(Stmt stmt)
    {
        String kind = "unknown";
        if(stmt instanceof AssignStmt)
            kind = "assign";
        else if(stmt instanceof DefinitionStmt)
            kind = "definition";
        else if(stmt instanceof EnterMonitorStmt)
            kind = "enter-monitor";
        else if(stmt instanceof ExitMonitorStmt)
            kind = "exit-monitor";
        else if(stmt instanceof GotoStmt)
            kind = "goto";
        else if(stmt instanceof IdentityStmt)
            kind = "assign";
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
        else if(stmt instanceof ThrowStmt)
            kind = "throw";
        return kind;
    }

    String unsupported(SootMethod inMethod, Stmt stmt, int index)
    {
        return getMethodSignature(inMethod) +
            "/unsupported " + getKind(stmt) +
            "/" +  stmt.toString() +
            "/instruction" + index;
    }

    /**
     * Text representation of instruction to be used as refmode.
     */
    String instruction(SootMethod inMethod, Stmt stmt, Session session, int index)
    {
        return getMethodSignature(inMethod) + "/" + getKind(stmt) + "/instruction" + index;
    }

    String invoke(SootMethod inMethod, InvokeExpr expr, Session session)
    {
        SootMethod exprMethod = expr.getMethod();
        String defaultMid = exprMethod.getDeclaringClass() + "." + exprMethod.getName();
        String midPart = (expr instanceof DynamicInvokeExpr)?
            dynamicInvokeMiddlePart((DynamicInvokeExpr)expr, defaultMid) : defaultMid;

        return getMethodSignature(inMethod) +
               "/" + midPart + "/" + session.nextNumber(midPart);
    }

    // Create a middle part for invokedynamic ids. It currently
    // supports the LambdaMetafactory machinery, returning a default
    // value for other (or missing) bootstrap methods.
    private String dynamicInvokeMiddlePart(DynamicInvokeExpr expr, String defaultResult) {

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
        return defaultResult;
    }


    String heapAlloc(SootMethod inMethod, AnyNewExpr expr, Session session)
    {
        if(expr instanceof NewExpr || expr instanceof NewArrayExpr)
        {
            return heapAlloc(inMethod, expr.getType(), session);
        }
        else if(expr instanceof NewMultiArrayExpr)
        {
            return heapAlloc(inMethod, expr.getType(), session);
            //      return getMethodSignature(inMethod) + "/" + type + "/" +  session.nextNumber(type);


        }
        else
        {
            throw new RuntimeException("Cannot handle new expression: " + expr);
        }
    }


    String heapMultiArrayAlloc(SootMethod inMethod, NewMultiArrayExpr expr, ArrayType type, Session session)
    {
        return heapAlloc(inMethod, type, session);
    }

    private String heapAlloc(SootMethod inMethod, Type type, Session session)
    {
        String s = type.toString();
        return getMethodSignature(inMethod) + "/new " + s + "/" +  session.nextNumber(s);


    }

    String methodHandleConstant(String handleName) {
        return "<handle " + handleName + ">";
    }

}
