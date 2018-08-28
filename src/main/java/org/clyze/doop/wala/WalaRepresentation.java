package org.clyze.doop.wala;

import com.ibm.wala.analysis.typeInference.TypeInference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.shrikeCT.BootstrapMethodsReader;
import com.ibm.wala.shrikeCT.ClassConstants;
import com.ibm.wala.shrikeCT.ConstantPoolParser;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.ssa.*;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;
import org.clyze.doop.common.JavaRepresentation;
import org.clyze.doop.common.SessionCounter;
import org.clyze.persistent.model.doop.DynamicMethodInvocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.clyze.doop.wala.WalaUtils.fixTypeString;

class WalaRepresentation extends JavaRepresentation {
    private Map<String, String> _methodSigRepr = new ConcurrentHashMap<>();

    /*
     * Each catch instruction is identified by the combination of: the method signature of the method it is in,
     * the ir variable def'ed by it and the scope number (to cover cases with multiple scopes for one catch, more right below)
     */
    private Map<String, String> _catchRepr = new ConcurrentHashMap<>();
    /*
     * For each handler that has more than one scope the number of scopes are stored on a map because they can be useful
     * Each different scope of a handler is represented by a different Exception_Handler fact
     * We use it when we need to produce Exception_Handler_Previous facts and need to find the last exception handler of a block
     */
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
        return JavaRepresentation.classConstant(fixTypeString(c.getName().toString()));
    }

    String classConstant(TypeReference t) {
        return JavaRepresentation.classConstant(fixTypeString(t.toString()));
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

    String simpleName(MethodReference mr) {
        return mr.getName().toString();
    }

    String simpleName(IField f) {
        return simpleName(f.getReference());
    }

    String simpleName(FieldReference f) {
        return f.getName().toString();
    }

    //Method descriptors using Soot-like format.
    //Should maybe cache these as well.
    String params(MethodReference methodReference)
    {
        StringBuilder builder = new StringBuilder();
        int count = methodReference.getNumberOfParameters();
        builder.append("(");
        for(int i = 0; i < count; i++)
        {
            builder.append(fixTypeString(methodReference.getParameterType(i).toString()));

            if(i != count - 1)
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
        return nativeReturnVarOfMethod(signature(m));
    }

    String param(IMethod m, int i)
    {
        return signature(m) + "/v" + (i+1);
    }

    String local(IMethod m, Local local)
    {
        return localId(signature(m), local.getName());
    }

    String newLocalIntermediate(IMethod m, Local l, SessionCounter counter)
    {
        return newLocalIntermediateId(local(m, l), counter);
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

    String handler(IMethod m, SSAGetCaughtExceptionInstruction catchInstr, TypeReference typeReference, SessionCounter counter, int scopeIndex)
    {
        String query = m.getSignature() + fixTypeString(typeReference.toString()) + " v" + catchInstr.getDef()+ "-" + scopeIndex;

        String result = _catchRepr.get(query);
        if(result == null) {
            String name = "catch " + fixTypeString(typeReference.toString());
            result = signature(m) + "/" + name + "/" + counter.nextNumber(name);
            _catchRepr.put(query,result);
        }
        return result;
    }

    String throwLocal(IMethod m, Local l, SessionCounter counter)
    {
        String name = "throw " + l.getName();
        return signature(m) + "/" + name + "/" + counter.nextNumber(name);
    }

    //This method takes a MethodReference as a parameter and it does not include "this" as an argument
    //Had the parameter been an IMethod it would include "this" but Soot signatures don't have it so we keep it this way.
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
        if(instruction instanceof SSAInstanceofInstruction || instruction instanceof  SSANewInstruction)
            kind = "assign";
        else if(instruction instanceof SSAArrayStoreInstruction || instruction instanceof SSAArrayLoadInstruction)
            kind = "assign";
        else if(instruction instanceof SSAConversionInstruction || instruction instanceof  SSACheckCastInstruction)
            kind = "assign";
        else if(instruction instanceof SSABinaryOpInstruction || instruction instanceof  SSAUnaryOpInstruction || instruction instanceof SSAArrayLengthInstruction)
            kind = "assign";
        else if(instruction instanceof  SSALoadMetadataInstruction)
            kind = "assign";
        else if(instruction instanceof  SSAGetInstruction || instruction instanceof SSAPutInstruction)
            kind = "assign";
        else if(instruction instanceof  SSAGetCaughtExceptionInstruction)
            kind = "definition";
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

    String unsupported(IMethod inMethod, IR ir, SSAInstruction instruction, int index)
    {
        return unsupportedId(signature(inMethod), getKind(instruction), instruction.toString(ir.getSymbolTable()).replace(" ", ""), index);
    }

    /**
     * Text representation of instruction to be used as refmode.
     */
    String instruction(IMethod inMethod, SSAInstruction instruction, int index) {
        return instructionId(signature(inMethod), getKind(instruction), index);
    }

    String invoke(IR ir, IMethod inMethod, SSAInvokeInstruction instr, MethodReference methRef, SessionCounter counter, TypeInference typeInference)
    {
        String defaultMid = fixTypeString(methRef.getDeclaringClass().toString()) + "." + methRef.getName().toString();
        String midPart;
        if (instr instanceof SSAInvokeDynamicInstruction)
            midPart = dynamicInvokeMiddlePart((SSAInvokeDynamicInstruction) instr, defaultMid);
        else
            midPart = invokeIdMiddle(ir, instr, methRef, typeInference);

        return invokeId(signature(inMethod), midPart, counter);
    }

    private String invokeIdMiddle(IR ir, SSAInvokeInstruction instr, MethodReference resolvedTargetRef, TypeInference typeInference) {
        MethodReference defaultTargetRef = instr.getDeclaredTarget();

        if (instr.isDispatch() || instr.isSpecial()) {
            Local l = WalaUtils.createLocal(ir, instr,instr.getReceiver(), typeInference);
            if(fixTypeString(l.getType().toString()).equals("java.lang.Object")) //Hack around faulty typeInference
                return fixTypeString(defaultTargetRef.getDeclaringClass().toString())+ "." + simpleName(resolvedTargetRef);
            else
                return fixTypeString(l.getType().toString())+ "." + simpleName(resolvedTargetRef);
        } else
            return fixTypeString(resolvedTargetRef.getDeclaringClass().toString())+ "." + simpleName(resolvedTargetRef);
    }

    // Create a middle part for invokedynamic ids. It currently
    // supports the LambdaMetafactory machinery, returning a default
    // value for other (or missing) bootstrap methods.
    private String dynamicInvokeMiddlePart(SSAInvokeDynamicInstruction instruction, String defaultResult) {

        // The signatures of the two lambda metafactories we currently support.
        final String DEFAULT_L_METAFACTORY = "<java.lang.invoke.LambdaMetafactory: java.lang.invoke.CallSite metafactory(java.lang.invoke.MethodHandles$Lookup,java.lang.String,java.lang.invoke.MethodType,java.lang.invoke.MethodType,java.lang.invoke.MethodHandle,java.lang.invoke.MethodType)>";
        final String ALT_L_METAFACTORY = "<java.lang.invoke.LambdaMetafactory: java.lang.invoke.CallSite altMetafactory(java.lang.invoke.MethodHandles$Lookup,java.lang.String,java.lang.invoke.MethodType,java.lang.Object[])>";

        BootstrapMethodsReader.BootstrapMethod bootMethRef = instruction.getBootstrap();
        ConstantPoolParser constantPool = bootMethRef.getCP();
        if (bootMethRef != null) {
            int bootArity = bootMethRef.callArgumentCount();
            if (bootArity > 1) {
                int argType = bootMethRef.callArgumentKind(1);
                int argIndex = bootMethRef.callArgumentIndex(1);

                String bootMethName = "<" + bootMethRef.methodClass().replace('/','.') + ": ";
                bootMethName += WalaUtils.createMethodSignature(bootMethRef.methodType(),bootMethRef.methodName()) + ">";
                if ((argType == ClassConstants.CONSTANT_MethodHandle) &&
                    ((bootMethName.equals(DEFAULT_L_METAFACTORY)) ||
                     (bootMethName.equals(ALT_L_METAFACTORY)))) {
                    try {
                        String declaringClass = constantPool.getCPHandleClass(argIndex).replace('/','.');
                        String name = constantPool.getCPHandleName(argIndex);
                        return DynamicMethodInvocation.genId(declaringClass, name);
                    } catch (InvalidClassFileException e) {
                        System.out.println("Representation: Unsupported invokedynamic, caught InvalidClassFileException returning default result.");
                        return defaultResult;
                    }
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


    String heapAlloc(IMethod inMethod, SSANewInstruction instruction, SessionCounter counter)
    {
        int newParams = instruction.getNumberOfUses();
        if(newParams == 0 || newParams == 1) {
            return heapAlloc(inMethod, instruction.getConcreteType(), counter);
        } else if(newParams > 1) {
            return heapAlloc(inMethod, instruction.getConcreteType(), counter);
        } else {
            throw new RuntimeException("Cannot handle new expression: " + instruction);
        }
    }


    String heapMultiArrayAlloc(IMethod inMethod, SSANewInstruction instruction, TypeReference type, SessionCounter counter) {
        return heapAlloc(inMethod, type, counter);
    }

    private String heapAlloc(IMethod inMethod, TypeReference type, SessionCounter counter) {
        String s = fixTypeString(type.toString());
        return signature(inMethod) + "/new " + s + "/" +  counter.nextNumber(s);


    }

}
