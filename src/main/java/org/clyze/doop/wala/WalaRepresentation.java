package org.clyze.doop.wala;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.shrikeCT.BootstrapMethodsReader;
import com.ibm.wala.ssa.*;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;
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

    //REVIEW: Delete this and keep getMethodSignature()?
    public String signature(IMethod m) {
        //return m.getSignature();
        return getMethodSignature(m);
    }

    public String signature(MethodReference m) {
        return m.getSignature();
    }

    String signature(IField f) {
        //return f.getReference().getSignature();
        StringBuilder DoopSig= new StringBuilder("<");
        DoopSig.append(fixTypeString(f.getDeclaringClass().toString()));
        DoopSig.append(": ");
        DoopSig.append(fixTypeString(f.getFieldTypeReference().toString()));
        DoopSig.append(" ");
        DoopSig.append(f.getName().toString());
        DoopSig.append(">");
        return DoopSig.toString();
    }

    String signature(FieldReference f) {
        return f.getSignature();
    }

    String simpleName(IMethod m) {
        return m.getSignature();
    }

    String simpleName(IField m) {
        return m.getReference().getSignature();
    }

    String simpleName(FieldReference f) {
        return f.getSignature();
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

    String param(IMethod m, int i)//REVIEW:SIFIS:I believe parameters are normal vi variables
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

    private String fixTypeString(String original)
    {
        boolean isArrayType = false;
        if(original.contains("[L")) //Figure out if this is correct
            isArrayType = true;
        String ret = original.substring(original.indexOf("L") +1).replaceAll("/",".").replaceAll(">","");
        String temp;
        if(ret.contains("Primordial"))
        {
            temp = ret.substring(ret.indexOf(",") + 1);
            if(temp.startsWith("["))
            {
                isArrayType = true;
                temp = temp.substring(1);
            }
            if(temp.equals("Z"))
                ret = "boolean";
            else if(temp.equals("I"))
                ret = "int";
            else if(temp.equals("V"))
                ret = "void";
            else if(temp.equals("B"))
                ret = "byte";
            else if(temp.equals("C"))
                ret = "char";
            else if(temp.equals("D"))
                ret = "double";
            else if(temp.equals("F"))
                ret = "float";
            else if(temp.equals("J"))
                ret = "long";
            else if(temp.equals("S"))
                ret = "short";
            //TODO: Figure out what the 'P' code represents in WALA's TypeReference
        }
        if(isArrayType)
            ret = ret + "[]";
        return ret;
    }

    //REVIEW: changed from WALA Signature to Doop-Soot like, maybe store them so we don't have to produce them like this every time?
    private String getMethodSignature(IMethod m)
    {
        //return m.getSignature();
        String DoopSig ="<"+ fixTypeString(m.getDeclaringClass().toString())+": "+ fixTypeString(m.getReturnType().toString()) + " " + m.getReference().getName()+"(";
        for (int i = 0; i < m.getNumberOfParameters(); i++) {
            DoopSig+=fixTypeString(m.getParameterType(i).toString());
            if (i < m.getNumberOfParameters() - 1)
                DoopSig+=",";
        }
        DoopSig+=")>";
        return DoopSig;
    }

    private String getKind(SSAInstruction instruction)
    {
        String kind = "unknown";
        if(instruction instanceof AssignStmt)
            kind = "assign";
        else if(instruction instanceof DefinitionStmt)
            kind = "definition";
        else if(instruction instanceof EnterMonitorStmt && ((SSAMonitorInstruction) instruction).isMonitorEnter())
            kind = "enter-monitor";
        else if(instruction instanceof SSAMonitorInstruction )
            kind = "exit-monitor";
        else if(instruction instanceof SSAGotoInstruction)
            kind = "goto";
        else if(instruction instanceof IdentityStmt)
            kind = "assign";
        else if(instruction instanceof IfStmt)
            kind = "if";
        else if(instruction instanceof SSAInvokeInstruction)
            kind = "invoke";
        else if(instruction instanceof SSAReturnInstruction)
            kind = "ret";
        else if(instruction instanceof SSAReturnInstruction && ((SSAReturnInstruction) instruction).returnsVoid())
            kind = "return-void";
        else if(instruction instanceof SSAReturnInstruction)
            kind = "return";
        else if(instruction instanceof SSAThrowInstruction)
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
    String instruction(IMethod inMethod, SSAInstruction instruction, Session session, int index)
    {
        return getMethodSignature(inMethod) + "/" + getKind(instruction) + "/instruction" + index;
    }
    String invoke(IMethod inMethod, SSAInvokeInstruction expr, Session session)
    {
        MethodReference exprMethod = expr.getDeclaredTarget();
        String defaultMid = exprMethod.getDeclaringClass() + "." + exprMethod.getName();
        String midPart = (expr instanceof SSAInvokeDynamicInstruction)? dynamicInvokeMiddlePart((SSAInvokeDynamicInstruction) expr, defaultMid) : defaultMid;

        return getMethodSignature(inMethod) + "/" + midPart + "/" + session.nextNumber(midPart);
    }

    // Create a middle part for invokedynamic ids. It currently
    // supports the LambdaMetafactory machinery, returning a default
    // value for other (or missing) bootstrap methods.
    private String dynamicInvokeMiddlePart(SSAInvokeDynamicInstruction instruction, String defaultResult) {

        // The signatures of the two lambda metafactories we currently support.
        final String DEFAULT_L_METAFACTORY = "<java.lang.invoke.LambdaMetafactory: java.lang.invoke.CallSite metafactory(java.lang.invoke.MethodHandles$Lookup,java.lang.String,java.lang.invoke.MethodType,java.lang.invoke.MethodType,java.lang.invoke.MethodHandle,java.lang.invoke.MethodType)>";
        final String ALT_L_METAFACTORY = "<java.lang.invoke.LambdaMetafactory: java.lang.invoke.CallSite altMetafactory(java.lang.invoke.MethodHandles$Lookup,java.lang.String,java.lang.invoke.MethodType,java.lang.Object[])>";

        BootstrapMethodsReader.BootstrapMethod bootMethRef= instruction.getBootstrap();
        if (bootMethRef != null) {
            String bootMethName = bootMethRef.methodName();
            int bootArity = bootMethRef.callArgumentCount();
            if (bootArity > 1) {
//                bootMethRef.callArgumentKind(1);
//                Value val1 = instruction.(1);
//                if ((val1 instanceof MethodHandle) &&
//                    ((bootMethName.equals(DEFAULT_L_METAFACTORY)) ||
//                     (bootMethName.equals(ALT_L_METAFACTORY)))) {
//                    IMethodRef smr = ((MethodHandle)val1).getMethodRef();
//                    return DynamicMethodInvocation.genId(smr.declaringClass().toString(),
//                            smr.name());
//                }
//                else
//                    System.out.println("Representation: Unsupported invokedynamic, unknown boot method " + bootMethName + ", arity=" + bootArity);
            }
            else
                System.out.println("Representation: Unsupported invokedynamic (unknown boot method of arity 0)");
        }
        else
            System.out.println("Representation: Malformed invokedynamic (null bootmethod)");
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
