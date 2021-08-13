package org.clyze.doop.wala;

import com.ibm.wala.analysis.typeInference.TypeInference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.shrikeCT.BootstrapMethodsReader;
//import com.ibm.wala.shrikeCT.ClassConstants;
//import com.ibm.wala.shrikeCT.ConstantPoolParser;
//import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.ssa.*;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.clyze.doop.common.JavaRepresentation;
import org.clyze.doop.common.SessionCounter;
import org.clyze.persistent.model.jvm.JvmDynamicMethodInvocation;

import static org.clyze.doop.wala.WalaUtils.fixTypeString;

class WalaRepresentation extends JavaRepresentation {
    private final Map<String, String> _methodSigRepr = new ConcurrentHashMap<>();

    /*
     * Each catch instruction is identified by the combination of: the method signature of the method it is in,
     * the ir variable def'ed by it and the scope number (to cover cases with multiple scopes for one catch, more right below)
     */
    private final Map<String, String> _catchRepr = new ConcurrentHashMap<>();
    /*
     * For each handler that has more than one scope the number of scopes are stored on a map because they can be useful
     * Each different scope of a handler is represented by a different Exception_Handler fact
     * We use it when we need to produce Exception_Handler_Previous facts and need to find the last exception handler of a block
     */
    private final Map<String, Integer> _handlerNumOfScopes = new ConcurrentHashMap<>();

    static String classConstant(IClass c) {
        return classConstant(fixTypeString(c.getName().toString()));
    }

    static String classConstant(TypeReference t) {
        return classConstant(fixTypeString(t.toString()));
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

    static String signature(IField f) {
        //return f.getReference().getSignature();
        return signature(f.getReference(), f.getReference().getDeclaringClass());
    }

    static String signature(FieldReference f, TypeReference declaringClass) {
        return "<" + fixTypeString(declaringClass.toString()) + ": " +
                fixTypeString(f.getFieldType().toString()) + " " +
                f.getName().toString() + ">";
    }

    static String simpleName(MethodReference mr) {
        return mr.getName().toString();
    }

    static String simpleName(IField f) {
        return simpleName(f.getReference());
    }

    private static String simpleName(FieldReference f) {
        return f.getName().toString();
    }

    // Method descriptors using Soot-like format.
    // Should maybe cache these as well.
    static String params(MethodReference methodReference) {
        StringBuilder builder = new StringBuilder();
        int count = methodReference.getNumberOfParameters();
        for(int i = 0; i < count; i++) {
            builder.append(fixTypeString(methodReference.getParameterType(i).toString()));
            if(i != count - 1)
                builder.append(",");
        }
        return builder.toString();
    }

    String thisVar(IMethod m) {
        return signature(m) + "/v1";
    }

    String nativeReturnVar(IMethod m) {
        return nativeReturnVarOfMethod(signature(m));
    }

    String param(IMethod m, int i) {
        return signature(m) + "/v" + (i+1);
    }

    String local(IMethod m, Local local) {
        return localId(signature(m), local.getName());
    }

    String newLocalIntermediate(IMethod m, Local l, SessionCounter counter) {
        return newLocalIntermediateId(local(m, l), counter);
    }

    void putHandlerNumOfScopes(IMethod m, SSAGetCaughtExceptionInstruction catchInstr, int scopeIndex) {
        String handler = m.getSignature() + " v" + catchInstr.getDef();
        _handlerNumOfScopes.put(handler, scopeIndex);
    }

    int getHandlerNumOfScopes(IMethod m, SSAGetCaughtExceptionInstruction catchInstr) {
        String handler = m.getSignature() + " v" + catchInstr.getDef();
        Integer numOfScopes = _handlerNumOfScopes.get(handler);
        if(numOfScopes == null)
            return 0;
        else
            return numOfScopes;
    }

    String handler(IMethod m, SSAGetCaughtExceptionInstruction catchInstr, TypeReference typeReference, SessionCounter counter, int scopeIndex) {
        String query = m.getSignature() + fixTypeString(typeReference.toString()) + " v" + catchInstr.getDef()+ "-" + scopeIndex;

        String result = _catchRepr.get(query);
        if(result == null) {
            String name = handlerMid(fixTypeString(typeReference.toString()));
            result = numberedInstructionId(signature(m), name, counter);
            _catchRepr.put(query,result);
        }
        return result;
    }

    String throwLocal(IMethod m, Local l, SessionCounter counter) {
        String name = throwLocalId(l.getName());
        return numberedInstructionId(signature(m), name, counter);
    }

    // This method takes a MethodReference as a parameter and does not include
    // "this" as an argument. Had the parameter been an IMethod it would
    // include "this" but Soot signatures don't have it, so we keep it this way.
    private static String createMethodSignature(MethodReference m) {
        StringBuilder DoopSig = new StringBuilder("<" + fixTypeString(m.getDeclaringClass().toString()) + ": " + fixTypeString(m.getReturnType().toString()) + " " + m.getName() + "(");
        for (int i = 0; i < m.getNumberOfParameters(); i++) {
            DoopSig.append(fixTypeString(m.getParameterType(i).toString()));
            if (i < m.getNumberOfParameters() - 1)
                DoopSig.append(",");
        }
        DoopSig.append(")>");
        return DoopSig.toString();
    }

    private static String getKind(SSAInstruction instruction) {
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

    String unsupported(IMethod inMethod, IR ir, SSAInstruction instruction, int index) {
        return unsupportedId(signature(inMethod), getKind(instruction), instruction.toString(ir.getSymbolTable()).replace(" ", ""), index);
    }

    /**
     * Text representation of instruction to be used as refmode.
     */
    String instruction(IMethod inMethod, SSAInstruction instruction, int index) {
        return instructionId(signature(inMethod), getKind(instruction), index);
    }

    String invoke(IR ir, IMethod inMethod, SSAInvokeInstruction instr, MethodReference methRef, SessionCounter counter, TypeInference typeInference) {
        String defaultMid = fixTypeString(methRef.getDeclaringClass().toString()) + "." + methRef.getName().toString();
        String midPart;
        if (instr instanceof SSAInvokeDynamicInstruction)
            midPart = dynamicInvokeMiddlePart((SSAInvokeDynamicInstruction) instr, defaultMid);
        else
            midPart = invokeIdMiddle(ir, instr, methRef, typeInference);

        return numberedInstructionId(signature(inMethod), midPart, counter);
    }

    private static String invokeIdMiddle(IR ir, SSAInvokeInstruction instr, MethodReference resolvedTargetRef, TypeInference typeInference) {
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

    // Create a middle part for invokedynamic ids. Returns a default
    // value for missing bootstrap methods.
    private static String dynamicInvokeMiddlePart(SSAInvokeDynamicInstruction instruction, String defaultResult) {
        BootstrapMethodsReader.BootstrapMethod bootMethRef = instruction.getBootstrap();
        if (bootMethRef == null) {
            System.err.println("Unsupported invokedynamic, null boot method.");
            return defaultResult;
        }
        String bootName = bootMethRef.methodName();
        String dynName = instruction.getCallSite().getDeclaredTarget().getName().toString();
        return JvmDynamicMethodInvocation.genericId(bootName, dynName);
    }

    String heapAlloc(IMethod inMethod, SSANewInstruction instruction, SessionCounter counter) {
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
        return heapAllocId(signature(inMethod), s, counter);
    }
}
