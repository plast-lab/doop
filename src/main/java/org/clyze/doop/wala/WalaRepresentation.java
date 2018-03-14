package org.clyze.doop.wala;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAReturnInstruction;
import com.ibm.wala.types.TypeReference;
import org.clyze.doop.soot.Session;
import soot.*;
import soot.jimple.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WalaRepresentation {
    private Map<IMethod, String> _methodRepr = new ConcurrentHashMap<>();
    private Map<IMethod, String> _methodSigRepr = new ConcurrentHashMap<>();
    private Map<IMethod, String> _methodRefSigRepr = new ConcurrentHashMap<>();
    private Map<Trap, String> _trapRepr = new ConcurrentHashMap<>();

    // Make it a trivial singleton.
    private static WalaRepresentation _repr;
    private WalaRepresentation() {}

    public static WalaRepresentation getRepresentation() {
        if (_repr == null)
            _repr = new WalaRepresentation();
        return _repr;
    }

    String classConstant(IClass c) {
        return "<class " + c.getName().getClassName().toString() + ">";
    }

    String classConstant(String className) {
        return "<class " + className + ">";
    }

    String classConstant(TypeReference t) {
        return "<class " + t + ">";
    }

    public String signature(IMethod m) {
        String result = _methodSigRepr.get(m);

        if(result == null)
        {
            result = m.getSignature();
            _methodSigRepr.put(m, result);
        }

        return result;
    }

    String signature(IField f) {
        return f.getReference().getSignature();
    }

    String simpleName(IMethod m) {
        return m.getSignature();
    }

    String simpleName(IField m) {
        return m.getReference().getSignature();
    }

    String descriptor(IMethod m)
    {
        StringBuilder builder = new StringBuilder();

        builder.append(m.getReturnType().toString());
        builder.append("(");
        for(int i = 0; i < m.getNumberOfParameters(); i++)
        {
            builder.append(m.getParameterType(i));

            if(i != m.getNumberOfParameters() - 1)
            {
                builder.append(",");
            }
        }
        builder.append(")");

        return builder.toString();
    }

    String thisVar(IMethod m)
    {
        return getMethodSignature(m) + "/@this";
    }

    String nativeReturnVar(IMethod m)
    {
        return getMethodSignature(m) + "/@native-return";
    }

    String param(IMethod m, int i)
    {
        return getMethodSignature(m) + "/@parameter" + i;
    }

    String local(IMethod m, Local local)
    {
        return getMethodSignature(m) + "/" + local.getName();
    }

    String newLocalIntermediate(IMethod m, Local l, Session session)
    {
        //String s = local(m, l);
        return "/intermediate/";
    }

    String handler(IMethod m, Trap trap, Session session)
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

    String throwLocal(IMethod m, Local l, Session session)
    {
        String name = "throw " + l.getName();
        return getMethodSignature(m) + "/" + name + "/" + session.nextNumber(name);
    }

    private String getMethodSignature(IMethod m)
    {
        return m.getSignature();
    }

    private String getKind(SSAInstruction instruction)
    {
        String kind = "unknown";
        if(instruction instanceof AssignStmt)
            kind = "assign";
        else if(instruction instanceof DefinitionStmt)
            kind = "definition";
        else if(instruction instanceof EnterMonitorStmt)
            kind = "enter-monitor";
        else if(instruction instanceof ExitMonitorStmt)
            kind = "exit-monitor";
        else if(instruction instanceof GotoStmt)
            kind = "goto";
        else if(instruction instanceof IdentityStmt)
            kind = "assign";
        else if(instruction instanceof IfStmt)
            kind = "if";
        else if(instruction instanceof InvokeStmt)
            kind = "invoke";
        else if(instruction instanceof RetStmt)
            kind = "ret";
        else if(instruction instanceof SSAReturnInstruction && ((SSAReturnInstruction) instruction).returnsVoid())
            kind = "return-void";
        else if(instruction instanceof SSAReturnInstruction)
            kind = "return";
        else if(instruction instanceof ThrowStmt)
            kind = "throw";
        return kind;
    }

    String unsupported(IMethod inMethod, SSAInstruction instruction, int index)
    {
        return getMethodSignature(inMethod) +
            "/unsupported " + getKind(instruction) +
            "/" +  instruction.toString() +
            "/instruction" + index;
    }

    /**
     * Text representation of instruction to be used as refmode.
     */
    String instruction(IMethod inMethod, SSAInstruction instruction)
    {
        return getMethodSignature(inMethod) + "/" + getKind(instruction) + "/instruction" + instruction.iindex;
    }

    String invoke(IMethod inMethod, InvokeExpr expr, Session session)
    {
//        IMethod exprMethod = expr.getMethod();
//        String defaultMid = exprMethod.getDeclaringClass() + "." + exprMethod.getName();
//        String midPart = (expr instanceof DynamicInvokeExpr)?
//            dynamicInvokeMiddlePart((DynamicInvokeExpr)expr, defaultMid) : defaultMid;
//
        return getMethodSignature(inMethod);
//              + "/" + midPart + "/" + session.nextNumber(midPart);
    }

    // Create a middle part for invokedynamic ids. It currently
    // supports the LambdaMetafactory machinery, returning a default
    // value for other (or missing) bootstrap methods.
    private String dynamicInvokeMiddlePart(DynamicInvokeExpr expr, String defaultResult) {

        // The signatures of the two lambda metafactories we currently support.
        final String DEFAULT_L_METAFACTORY = "<java.lang.invoke.LambdaMetafactory: java.lang.invoke.CallSite metafactory(java.lang.invoke.MethodHandles$Lookup,java.lang.String,java.lang.invoke.MethodType,java.lang.invoke.MethodType,java.lang.invoke.MethodHandle,java.lang.invoke.MethodType)>";
        final String ALT_L_METAFACTORY = "<java.lang.invoke.LambdaMetafactory: java.lang.invoke.CallSite altMetafactory(java.lang.invoke.MethodHandles$Lookup,java.lang.String,java.lang.invoke.MethodType,java.lang.Object[])>";

        //IMethodRef bootMethRef = expr.getBootstrapMethodRef();
//        if (bootMethRef != null) {
//            String bootMethName = bootMethRef.resolve().toString();
//            int bootArity = expr.getBootstrapArgCount();
//            if (bootArity > 1) {
//                Value val1 = expr.getBootstrapArg(1);
//                if ((val1 instanceof MethodHandle) &&
//                    ((bootMethName.equals(DEFAULT_L_METAFACTORY)) ||
//                     (bootMethName.equals(ALT_L_METAFACTORY)))) {
//                    IMethodRef smr = ((MethodHandle)val1).getMethodRef();
//                    return DynamicMethodInvocation.genId(smr.declaringClass().toString(),
//                            smr.name());
//                }
//                else
//                    System.out.println("Representation: Unsupported invokedynamic, unknown boot method " + bootMethName + ", arity=" + bootArity);
//            }
//            else
//                System.out.println("Representation: Unsupported invokedynamic (unknown boot method of arity 0)");
//        }
//        else
//            System.out.println("Representation: Malformed invokedynamic (null bootmethod)");
        return defaultResult;
    }


    String heapAlloc(IMethod inMethod, AnyNewExpr expr, Session session)
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


    String heapMultiArrayAlloc(IMethod inMethod, NewMultiArrayExpr expr, ArrayType type, Session session)
    {
        return heapAlloc(inMethod, type, session);
    }

    private String heapAlloc(IMethod inMethod, Type type, Session session)
    {
        String s = type.toString();
        return getMethodSignature(inMethod) + "/new " + s + "/" +  session.nextNumber(s);


    }

    String methodHandleConstant(String handleName) {
        return "<handle " + handleName + ">";
    }

}
