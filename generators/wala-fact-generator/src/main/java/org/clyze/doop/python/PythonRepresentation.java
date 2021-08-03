package org.clyze.doop.python;

import com.ibm.wala.analysis.typeInference.TypeInference;
import com.ibm.wala.cast.ir.ssa.AstEchoInstruction;
import com.ibm.wala.cast.ir.ssa.AstYieldInstruction;
import com.ibm.wala.cast.python.ssa.PythonInvokeInstruction;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ssa.*;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;
import org.clyze.doop.common.SessionCounter;
import org.clyze.doop.wala.Local;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.clyze.doop.python.utils.PythonUtils.fixNewType;
import static org.clyze.doop.python.utils.PythonUtils.fixType;

class PythonRepresentation {
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


    private String _fileName;
    // Make it a trivial singleton.
    private static PythonRepresentation _repr;
    private PythonRepresentation() {}

    static PythonRepresentation getRepresentation() {
        if (_repr == null)
            _repr = new PythonRepresentation();
        return _repr;
    }

    public PythonRepresentation(String fileName)
    {
        _fileName = fileName;
    }

    String classConstant(String className) {
        return "<class " + className + ">";
    }


    String classConstant(TypeReference t) {
        return "<class " + fixType(t) + ">";
    }

    String classConstant(IClass c) {
        return "<class " + classType(c) + ">";
    }

    //TODO: Have correct module name, opened an issue about it.
    String classType(IClass klass) {
        String cName = klass.getName().toString().substring(1);
        String[] classNameParts = cName.split("/");
        String declaringModule;
        //System.out.println(cName);
        String className = "";
        if(classNameParts.length >= 2){
            //declaringModule = classNameParts[0].replace("script ","");
            declaringModule = klass.getSourceFileName();
            for(int i=1; i < classNameParts.length; i++) {
                className += classNameParts[i];
                if(i!= classNameParts.length -1)
                    className+=":";
            }
            return "<" + declaringModule + ":" + className + ">";
        }
        else {
            declaringModule = "BUILTIN";
            className = classNameParts[0];
            return "<" +className + ">";
        }
        //return "<" + declaringModule + ":" + className + ">";
    }

    String methodTypeConstant(String s) {
        return s;
    }

    private String sourceFileName(IClass klass){
        return "<" + klass.getSourceFileName() + ">";
    }

    String sourceFileName(IMethod m){
        return sourceFileName(m.getDeclaringClass());
    }

    String signature(IMethod m) {
        //return signature(m.getReference());
        String sourceFileName = m.getDeclaringClass().getSourceFileName();
        String sourceFolderName = sourceFileName.substring(0, sourceFileName.lastIndexOf("/") + 1);
        String functionName = m.getDeclaringClass().getName().toString().substring(1).replaceFirst("script ","").replace("/",":");
        String methSig = "<" + sourceFolderName + functionName + ">";
        _methodSigRepr.putIfAbsent(m.getDeclaringClass().getName().toString().substring(1), methSig);
        return methSig;
    }

    String getSigByName(String name){
        String methSig = _methodSigRepr.get(name);
        if(methSig == null)
            return name;
        return methSig;
    }


    String signature(IField f) {
        String sourceFileName = f.getDeclaringClass().getSourceFileName();
        String declaringClass = f.getDeclaringClass().getName().toString();
        declaringClass = declaringClass.substring(declaringClass.indexOf(".py") + 4);
        String fieldName = f.getName().toString();
        return "<" + sourceFileName + ":" + declaringClass +  ":" + fieldName + ">";
    }


    String signature(FieldReference f, TypeReference declaringClass) {
        return "<" + fixType(declaringClass) +
                ": " +
                fixType(f.getFieldType()) +
                " " +
                f.getName().toString() +
                ">";
    }

    String simpleName(IMethod m) {
        String[] splitClassName = m.getDeclaringClass().getName().toString().split("/");
        String methodName = splitClassName[splitClassName.length - 1];
        methodName = methodName.replace("Lscript ","");
        return methodName;
    }

    String simpleName(IField f) {
        return simpleName(f.getReference());
    }

    String simpleName(FieldReference f) {
        return f.getName().toString();
    }

    //Method descriptors using soot like format.
    //Should maybe cache these as well.
    String params(IMethod m)
    {
        StringBuilder builder = new StringBuilder();
        int count = m.getNumberOfParameters();
        builder.append("(");
        for(int i = 0; i < count; i++)
        {
            builder.append(fixType(m.getParameterType(i)));

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
        return signature(m) + "/@native-return";
    }

    String param(IMethod m, int i)
    {
        return signature(m) + "/v" + (i+1);
    }

    String local(IMethod m, Local local)
    {
        return signature(m) + "/" + local.getName();
    }

    String newLocalIntermediate(IMethod m, Local l, SessionCounter counter)
    {
        String s = local(m, l);
        return s + "/intermediate/" + counter.nextNumber(s);
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
        String query = m.getSignature() + fixType(typeReference) + " v" + catchInstr.getDef()+ "-" + scopeIndex;

        String result = _catchRepr.get(query);
        if(result == null) {
            String name = "catch " + fixType(typeReference);
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
    //Had the parameter been an IMethod it would include "this" but soot Signatures don't have it so we keep it this way.
    private String createMethodSignature(MethodReference m)
    {
        StringBuilder DoopSig = new StringBuilder("<" + fixType(m.getDeclaringClass()) + ": " + fixType(m.getReturnType()) + " " + m.getName() + "(");
        for (int i = 0; i < m.getNumberOfParameters(); i++) {
            DoopSig.append(fixType(m.getParameterType(i)));
            if (i < m.getNumberOfParameters() - 1)
                DoopSig.append(",");
        }
        DoopSig.append(")>");
        return DoopSig.toString();
    }

    private String getKind(SSAInstruction instruction)
    {
        String kind = "unknown";
        if(instruction instanceof SSAInstanceofInstruction || instruction instanceof SSANewInstruction)
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
        else if(instruction instanceof AstEchoInstruction)
            kind = "print";
        else if(instruction instanceof AstYieldInstruction)
            kind = "yield";
        return kind;
    }

    String unsupported(IMethod inMethod, IR ir, SSAInstruction instruction, int index)
    {
        return signature(inMethod) +
                "/unsupported " + getKind(instruction) +
                "/" +  instruction.toString(ir.getSymbolTable()).replace(" ", "") +
                "/" + index;
    }

    /**
     * Text representation of instruction to be used as refmode.
     */
    String instruction(IMethod inMethod, SSAInstruction instruction, SessionCounter counter, int index)
    {
        if(instruction instanceof PythonInvokeInstruction)
            return functionInvoke(inMethod, (PythonInvokeInstruction) instruction, counter);
        else if(instruction instanceof SSAAbstractInvokeInstruction){
            if(((SSAAbstractInvokeInstruction) instruction).isStatic() && ((SSAAbstractInvokeInstruction) instruction).getDeclaredTarget().getName().toString().equals("import"))
            {
                String module = fixType(((SSAAbstractInvokeInstruction) instruction).getDeclaredTarget().getReturnType());
                return signature(inMethod) + "/import/" + module;
            }
        }

        return signature(inMethod) + "/" + getKind(instruction) + "/" + index;
    }

    String functionInvoke(IMethod inMethod, PythonInvokeInstruction insn, SessionCounter counter)
    {
        //TODO: REVISIT THIS AT SOME POINT
        //return signature(inMethod) + "/invoke/" + counter.nextNumber("invoke");
        return signature(inMethod) + "/invoke/" + insn.iindex;
    }

    //Will become obsolete
    String invoke(IR ir, IMethod inMethod, SSAAbstractInvokeInstruction instr, MethodReference methRef, SessionCounter counter, TypeInference typeInference)
    {
        String defaultMid = fixType(methRef.getDeclaringClass()) + "." + methRef.getName().toString();
        String midPart;
        if(instr instanceof PythonInvokeInstruction)
            midPart = "invoke";
        else
            midPart = defaultMid;

        return signature(inMethod) + "/" + midPart + "/" + counter.nextNumber(midPart);
    }

    String heapAlloc(IMethod inMethod, SSANewInstruction instruction, SessionCounter counter) {
        int newParams = instruction.getNumberOfUses();
        if(newParams == 0 || newParams == 1) {
            return heapAlloc(inMethod, instruction, instruction.getConcreteType(), counter);
        } else {
            throw new RuntimeException("Cannot handle new expression: " + instruction);
        }
    }


    String heapMultiArrayAlloc(IMethod inMethod, SSANewInstruction instruction, TypeReference type, SessionCounter counter)
    {
        return heapAlloc(inMethod, instruction, type, counter);
    }

    private String heapAlloc(IMethod inMethod, SSANewInstruction instruction, TypeReference type, SessionCounter counter)
    {
        String s = fixNewType(inMethod, instruction, type);

        return signature(inMethod) + "/new " + s + "/" +  counter.nextNumber(s);


    }
}
