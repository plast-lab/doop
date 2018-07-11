package org.clyze.doop.python;

import com.ibm.wala.analysis.typeInference.TypeInference;
import com.ibm.wala.cast.python.ssa.PythonInvokeInstruction;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ssa.*;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;
import org.clyze.doop.wala.Local;
import org.clyze.doop.wala.Session;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.clyze.doop.python.utils.PythonUtils.fixNewType;
import static org.clyze.doop.python.utils.PythonUtils.fixType;

public class PythonRepresentation {
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
        }
        else {
            declaringModule = "BUILTIN";
            className = classNameParts[0];
        }
        return declaringModule + ":" + className;
    }

    String methodTypeConstant(String s) {
        return s;
    }

    String signature(IMethod m) {
        //return signature(m.getReference());
        String sourceFileName = m.getDeclaringClass().getSourceFileName();
        String sourceFolderName = sourceFileName.substring(0, sourceFileName.lastIndexOf("/") + 1);
        String functionName = m.getDeclaringClass().getName().toString().substring(1).replaceFirst("script ","").replace("/",":");
        return "<" + sourceFolderName + functionName + ">";
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
        String sourceFileName = f.getDeclaringClass().getSourceFileName();
        String declaringClass = f.getDeclaringClass().getName().toString();
        declaringClass = declaringClass.substring(declaringClass.indexOf(".py") + 4);
        String fieldName = f.getName().toString();
        return "<" + sourceFileName + ":" + declaringClass +  ":" + fieldName + ">";
    }


    String signature(FieldReference f, TypeReference declaringClass) {
        StringBuilder DoopSig= new StringBuilder("<");
        DoopSig.append(fixType(declaringClass));
        DoopSig.append(": ");
        DoopSig.append(fixType(f.getFieldType()));
        DoopSig.append(" ");
        DoopSig.append(f.getName().toString());
        DoopSig.append(">");
        return DoopSig.toString();
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

    String newLocalIntermediate(IMethod m, Local l, Session session)
    {
        String s = local(m, l);
        return s + "/intermediate/" + session.nextNumber(s);
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
        String query = m.getSignature() + fixType(typeReference) + " v" + catchInstr.getDef()+ "-" + scopeIndex;

        String result = _catchRepr.get(query);
        if(result == null) {
            String name = "catch " + fixType(typeReference);
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
        return kind;
    }

    String unsupported(IMethod inMethod, IR ir, SSAInstruction instruction, int index)
    {
        return signature(inMethod) +
                "/unsupported " + getKind(instruction) +
                "/" +  instruction.toString(ir.getSymbolTable()).replace(" ", "") +
                "/instruction" + index;
    }

    /**
     * Text representation of instruction to be used as refmode.
     */
    String instruction(IMethod inMethod, SSAInstruction instruction, Session session, int index)
    {
        return signature(inMethod) + "/" + getKind(instruction) + "/instruction" + index;
    }
    String invoke(IR ir, IMethod inMethod, SSAAbstractInvokeInstruction instr, MethodReference methRef, Session session, TypeInference typeInference)
    {
        String defaultMid = fixType(methRef.getDeclaringClass()) + "." + methRef.getName().toString();
        String midPart;
        if(instr instanceof PythonInvokeInstruction)
            midPart = "invoke";
        else
            midPart = defaultMid;

        return signature(inMethod) + "/" + midPart + "/" + session.nextNumber(midPart);
    }

    String heapAlloc(IMethod inMethod, SSANewInstruction instruction, Session session)
    {
        int newParams = instruction.getNumberOfUses();
        if(newParams == 0 || newParams == 1) //
        {
            return heapAlloc(inMethod, instruction, instruction.getConcreteType(), session);
        }
        else
        {
            throw new RuntimeException("Cannot handle new expression: " + instruction);
        }
    }


    String heapMultiArrayAlloc(IMethod inMethod, SSANewInstruction instruction, TypeReference type, Session session)
    {
        return heapAlloc(inMethod, instruction, type, session);
    }

    private String heapAlloc(IMethod inMethod, SSANewInstruction instruction, TypeReference type, Session session)
    {
        String s = fixNewType(inMethod, instruction, type);

        return signature(inMethod) + "/new " + s + "/" +  session.nextNumber(s);


    }
}
