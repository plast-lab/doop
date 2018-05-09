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

class WalaRepresentation {
    private Map<String, String> _methodSigRepr = new ConcurrentHashMap<>();
    private Map<String, String> _catchRepr = new ConcurrentHashMap<>();
    private Map<String, Integer> _handlerNumOfScopes = new ConcurrentHashMap<>();

    // Make it a trivial singleton.
    private static WalaRepresentation _repr;
    private WalaRepresentation() {}

    static WalaRepresentation getRepresentation() {
        if (_repr == null)
            _repr = new WalaRepresentation();
        return _repr;
    }

    String classConstant(IClass c) {
        return "<class " + fixTypeString(c.getName().toString()) + ">";
    }

    String classConstant(String className) {
        return "<class " + className + ">";
    }


    String classConstant(TypeReference t) {
        return "<class " + fixTypeString(t.toString()) + ">";
    }


    String signature(IMethod m) {
        return signature(m.getReference());
    }

    String signature(MethodReference m) {
        String WalaSignature = m.getSignature();
        String doopSignature = _methodSigRepr.get(WalaSignature);
        if (doopSignature == null){
            doopSignature = createMethodSignature(m);
            _methodSigRepr.put(WalaSignature,doopSignature);
        }
        return doopSignature;
    }


    String signature(IField f) {
        //return f.getReference().getSignature();
        return signature(f.getReference(), f.getReference().getDeclaringClass());
    }


    String signature(FieldReference f, TypeReference declaringClass) {
        StringBuilder DoopSig= new StringBuilder("<");
        DoopSig.append(fixTypeString(declaringClass.toString()));
        DoopSig.append(": ");
        DoopSig.append(fixTypeString(f.getFieldType().toString()));
        DoopSig.append(" ");
        DoopSig.append(f.getName().toString());
        DoopSig.append(">");
        return DoopSig.toString();
    }

    String simpleName(IMethod m) {
        return m.getReference().getName().toString();
    }

    String simpleName(IField m) {
        return simpleName(m.getReference());
    }

    String simpleName(FieldReference f) {
        return f.getName().toString();
    }

    //Method descriptors using soot like format.
    //Should maybe cache these as well.
    String descriptor(IMethod m)
    {
        StringBuilder builder = new StringBuilder();
        MethodReference methodReference = m.getReference();
        builder.append(fixTypeString(methodReference.getReturnType().toString()));
        builder.append("(");
        for(int i = 0; i < methodReference.getNumberOfParameters(); i++)
        {
            builder.append(fixTypeString(methodReference.getParameterType(i).toString()));

            if(i != methodReference.getNumberOfParameters() - 1)
            {
                builder.append(",");
            }
        }
        builder.append(")");

        return builder.toString();
    }

    String thisVar(IMethod m)
    {
        return signature(m) + "/v1";
    }

    String nativeReturnVar(IMethod m)
    {
        return signature(m) + "/@native-return";
    }

    String param(IMethod m, int i)//REVIEW:SIFIS:I believe parameters are normal vi variables, same for this. Will look into it.
    {
        return signature(m) + "/v" + (i+1);
    }

    String local(IMethod m, Local local)
    {
        return signature(m) + "/" + local.getName();
    }

    String newLocalIntermediate(IMethod m, Local l, Session session)
    {
        //String s = local(m, l);
        return "/intermediate/";
    }

    void putHandlerNumOfScopes(IMethod m, SSAGetCaughtExceptionInstruction catchInstr, int scopeIndex)
    {

        String handler = m.getSignature() + " v" + catchInstr.getDef();
        _handlerNumOfScopes.put(handler, scopeIndex);

    }

    int getHandlerNumOfScopes(IMethod m, SSAGetCaughtExceptionInstruction catchInstr)
    {
        String handler = m.getSignature() + " v" + catchInstr.getDef();
        Integer numOfScopes = _handlerNumOfScopes.get(handler);
        if(numOfScopes == null)
            return 0;
        else
            return numOfScopes;
    }

    String handler(IMethod m, SSAGetCaughtExceptionInstruction catchInstr, TypeReference typeReference, Session session, int scopeIndex)
    {
        String query = m.getSignature() + " v" + catchInstr.getDef()+ "-" + scopeIndex;

        String result = _catchRepr.get(query);
        if(result == null) {
            String name = "catch " + fixTypeString(typeReference.toString());
            result = signature(m) + "/" + name + "/" + session.nextNumber(name);
            _catchRepr.put(query,result);
        }
        return result;
    }

    String throwLocal(IMethod m, Local l, Session session)
    {
        String name = "throw " + l.getName();
        return signature(m) + "/" + name + "/" + session.nextNumber(name);
    }

    static String fixTypeString(String original)
    {
        boolean isArrayType = false;
        int arrayTimes = 0;
        String ret;

        if(original.contains("L")) {
            if (original.contains("[")) //Figure out if this is correct
            {
                isArrayType = true;
                for (int i = 0; i < original.length(); i++) {
                    if (original.charAt(i) == '[')
                        arrayTimes++;
                }
            }
            ret = original.substring(original.indexOf("L") + 1).replaceAll("/", ".").replaceAll(">", "");
        }
        else {
            String temp;
            temp = original.substring(original.indexOf(",") + 1).replaceAll(">", "");
            if (temp.startsWith("[")) {
                isArrayType = true;
                for (int i = 0; i < temp.length(); i++) {
                    if (temp.charAt(i) == '[')
                        arrayTimes++;
                    else
                        break;

                }
                temp = temp.substring(arrayTimes);
            }
            switch (temp) {
                case "Z":
                    ret = "boolean";
                    break;
                case "I":
                    ret = "int";
                    break;
                case "V":
                    ret = "void";
                    break;
                case "B":
                    ret = "byte";
                    break;
                case "C":
                    ret = "char";
                    break;
                case "D":
                    ret = "double";
                    break;
                case "F":
                    ret = "float";
                    break;
                case "J":
                    ret = "long";
                    break;
                case "S":
                    ret = "short";
                    break;
                default:
                    ret = "OTHERPRIMITIVE";
                    break;
            }
            //TODO: Figure out what the 'P' code represents in WALA's TypeReference

        }
        if(isArrayType)
        {
            for(int i=0 ; i< arrayTimes ; i++)
                ret = ret + "[]";
        }
        //if(! ret.equals(fixTypeStringOld(original)) && ! original.contains("["))
        //System.out.println(original + " | " + ret + " | " + fixTypeStringOld(original));
        return ret;
    }

    //This method takes a MethodReference as a parameter and it does not include "this" as an argument
    //Had the parameter been an IMethod it would include "this" but soot Signatures don't have it so we keep it this way.
    private String createMethodSignature(MethodReference m)
    {
        StringBuilder DoopSig = new StringBuilder("<" + fixTypeString(m.getDeclaringClass().toString()) + ": " + fixTypeString(m.getReturnType().toString()) + " " + m.getName() + "(");
        for (int i = 0; i < m.getNumberOfParameters(); i++) {
            DoopSig.append(fixTypeString(m.getParameterType(i).toString()));
            if (i < m.getNumberOfParameters() - 1)
                DoopSig.append(",");
        }
        DoopSig.append(")>");
        return DoopSig.toString();
    }

    private String getKind(SSAInstruction instruction)
    {
        String kind = "unknown";
        if(instruction instanceof AssignStmt)//IMPORTANT:TODO: MAKE SURE WE COVER ALL ASSIGN CASES
            kind = "assign";
        else if(instruction instanceof DefinitionStmt)
            kind = "definition";
        else if(instruction instanceof SSAInstanceofInstruction || instruction instanceof  SSANewInstruction || instruction instanceof SSAArrayLoadInstruction)
            kind = "assign";
        else if(instruction instanceof SSAConversionInstruction || instruction instanceof  SSACheckCastInstruction)
            kind = "assign";
        else if(instruction instanceof SSABinaryOpInstruction || instruction instanceof  SSAUnaryOpInstruction)
            kind = "assign";
        else if(instruction instanceof SSAMonitorInstruction && ((SSAMonitorInstruction) instruction).isMonitorEnter())
            kind = "enter-monitor";
        else if(instruction instanceof SSAMonitorInstruction )
            kind = "exit-monitor";
        else if(instruction instanceof SSAGotoInstruction)
            kind = "goto";
        else if(instruction instanceof SSAConditionalBranchInstruction)
            kind = "if";
        else if(instruction instanceof SSAInvokeInstruction)
            kind = "invoke";
        else if(instruction instanceof SSAReturnInstruction && ((SSAReturnInstruction) instruction).returnsVoid())
            kind = "return-void";
        else if(instruction instanceof SSAReturnInstruction)
            kind = "ret";
        else if(instruction instanceof SSAThrowInstruction)
            kind = "throw";
        return kind;
    }

    String unsupported(IMethod inMethod, SSAInstruction instruction, int index)
    {
        return signature(inMethod) +
                "/unsupported " + getKind(instruction) +
                "/" +  instruction.toString() +
                "/instruction" + index;
    }

    /**
     * Text representation of instruction to be used as refmode.
     */
    String instruction(IMethod inMethod, SSAInstruction instruction, Session session, int index)
    {
        return signature(inMethod) + "/" + getKind(instruction) + "/instruction" + index;
    }
    String invoke(IMethod inMethod, SSAInvokeInstruction expr, MethodReference methRef, Session session)
    {
        //MethodReference exprMethod = expr.getDeclaredTarget();
        String defaultMid = fixTypeString(methRef.getDeclaringClass().toString()) + "." + methRef.getName().toString();
        String midPart = (expr instanceof SSAInvokeDynamicInstruction)? dynamicInvokeMiddlePart((SSAInvokeDynamicInstruction) expr, defaultMid) : defaultMid;

        return signature(inMethod) + "/" + midPart + "/" + session.nextNumber(midPart);
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


    String heapAlloc(IMethod inMethod, SSANewInstruction instruction, Session session)
    {
        int newParams = instruction.getNumberOfUses();
        if(newParams == 0 || newParams == 1) //
        {
            return heapAlloc(inMethod, instruction.getConcreteType(), session);
        }
        else if(newParams > 1)
        {
            return heapAlloc(inMethod, instruction.getConcreteType(), session);
        }
        else
        {
            throw new RuntimeException("Cannot handle new expression: " + instruction);
        }
    }


    String heapMultiArrayAlloc(IMethod inMethod, SSANewInstruction instruction, TypeReference type, Session session)
    {
        return heapAlloc(inMethod, type, session);
    }

    private String heapAlloc(IMethod inMethod, TypeReference type, Session session)
    {
        String s = fixTypeString(type.toString());
        return signature(inMethod) + "/new " + s + "/" +  session.nextNumber(s);


    }

    String methodHandleConstant(String handleName) {
        return "<handle " + handleName + ">";
    }

}
