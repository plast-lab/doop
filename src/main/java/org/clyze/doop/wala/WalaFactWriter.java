package org.clyze.doop.wala;

import com.ibm.wala.analysis.typeInference.TypeInference;
import com.ibm.wala.classLoader.*;
import com.ibm.wala.dalvik.classLoader.DexIMethod;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.shrikeCT.BootstrapMethodsReader;
import com.ibm.wala.shrikeCT.ClassConstants;
import com.ibm.wala.shrikeCT.ConstantPoolParser;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.ssa.*;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.types.annotations.Annotation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.clyze.doop.common.Database;
import org.clyze.doop.common.FactEncoders;
import org.clyze.doop.common.PredicateFile;
import soot.dexpler.DexMethod;

import javax.sound.midi.SysexMessage;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.clyze.doop.common.PredicateFile.*;
import static org.clyze.doop.wala.WalaUtils.*;

/**
 * FactWriter determines the format of a fact and adds it to a
 * database.
 */
public class WalaFactWriter {
    private boolean _android;
    private Database _db;
    private WalaRepresentation _rep;

    //Map from WALA's JVM like type string to our format
    //Used in writeType()
    private Map<String, String> _typeMap;

    //The Maps bellow are used as Sets in order not to put duplicate phantom* facts
    private Map<String, String> _phantomType;

    private Map<String, String> _phantomMethod;

    private Map<String, String> _phantomBasedMethod;

    private Map<String,List<String>> _signaturePolyMorphicMethods;

    //Used for logging various messages
    protected Log logger;

    WalaFactWriter(Database db, boolean android) {
        _android = android;
        _db = db;
        _rep = WalaRepresentation.getRepresentation();
        _typeMap = new ConcurrentHashMap<>();
        _phantomType = new ConcurrentHashMap<>();
        _phantomMethod = new ConcurrentHashMap<>();
        _phantomBasedMethod = new ConcurrentHashMap<>();
        logger =  LogFactory.getLog(getClass());
        _signaturePolyMorphicMethods = null;
    }

    public void setSignaturePolyMorphicMethods(Map<String,List<String>> signaturePolyMorphicMethods)
    {
        _signaturePolyMorphicMethods = signaturePolyMorphicMethods;
    }

    private String str(int i) {
        return String.valueOf(i);
    }

    int getNumberOfPhantomTypes()
    {
        return _phantomType.size();
    }

    int getNumberOfPhantomMethods()
    {
        return _phantomMethod.size();
    }

    int getNumberOfPhantomBasedMethods()
    {
        return _phantomBasedMethod.size();
    }

    private String writeStringConstant(String constant) {
        String raw = FactEncoders.encodeStringConstant(constant);

        String result;
        if(raw.length() <= 256)
            result = raw;
        else
            result = "<<HASH:" + raw.hashCode() + ">>";

        _db.add(STRING_RAW, result, raw);
        _db.add(STRING_CONST, result);

        return result;
    }

    private String hashMethodNameIfLong(String methodRaw) {
        if (methodRaw.length() <= 1024)
            return methodRaw;
        else
            return "<<METHOD HASH:" + methodRaw.hashCode() + ">>";
    }

    //The final argument is not translated to Soot's descriptor format but keeps WALAs JVM-like format as Soot is also using it.
    //This descriptor format is used by Soot only for Method.facts
    String writeMethod(IMethod m) {
        String result = _rep.signature(m);
        String arity = Integer.toString(m.getNumberOfParameters() - 1);
        if(m.isStatic())
            arity = Integer.toString(m.getNumberOfParameters());

        _db.add(STRING_RAW, result, result);
        _db.add(METHOD, result, _rep.simpleName(m.getReference()), _rep.params(m.getReference()), writeType(m.getReference().getDeclaringClass()), writeType(m.getReturnType()), m.getDescriptor().toUnicodeString(), arity);
        for (Annotation annotation : m.getAnnotations()) {
            _db.add(METHOD_ANNOTATION, result, fixTypeString(annotation.getType().toString()));
            //TODO:See if we can take use other features wala offers for annotations (named and unnamed arguments)
        }
//        ShrikeCTMethod shrikeMethod = (ShrikeCTMethod)m;
//        Collection<Annotation>[] paraAnnotations = shrikeMethod.getParameterAnnotations();
//        for(int i=0; i< paraAnnotations.length;i++)
//        {
//            annotationIterator = paraAnnotations[i].iterator();
//            while(annotationIterator.hasNext())
//            {
//                Annotation annotation = annotationIterator.next();
//                _db.add(PARAM_ANNOTATION, result, str(i), _rep.fixTypeString(annotation.getType().toString()));
//            }
//
//        }
//        if (m.getTag("VisibilityAnnotationTag") != null) {
//            VisibilityAnnotationTag vTag = (VisibilityAnnotationTag) m.getTag("VisibilityAnnotationTag");
//            for (AnnotationTag aTag : vTag.getAnnotations()) {
//                _db.add(METHOD_ANNOTATION, result, soot.coffi.Util.v().jimpleTypeOfFieldDescriptor(aTag.getType()).getEscapedName());
//            }
//        }
//        if (m.getTag("VisibilityParameterAnnotationTag") != null) {
//            VisibilityParameterAnnotationTag vTag = (VisibilityParameterAnnotationTag) m.getTag("VisibilityParameterAnnotationTag");
//
//            ArrayList<VisibilityAnnotationTag> annList = vTag.getVisibilityAnnotations();
//            for (int i = 0; i < annList.size(); i++) {
//                if (annList.get(i) != null) {
//                    for (AnnotationTag aTag : annList.get(i).getAnnotations()) {
//                        _db.add(PARAM_ANNOTATION, result, str(i), soot.coffi.Util.v().jimpleTypeOfFieldDescriptor(aTag.getType()).getEscapedName());
//                    }
//                }
//            }
//        }
        return result;
    }

    void writeClassArtifact(String artifact, String className) { _db.add(CLASS_ARTIFACT, artifact, className); }

    void writeAndroidEntryPoint(IMethod m) {
        _db.add(ANDROID_ENTRY_POINT, _rep.signature(m));
    }

    void writeProperty(String path, String key, String value) {
        String pathId = writeStringConstant(path);
        String keyId = writeStringConstant(key);
        String valueId = writeStringConstant(value);
        _db.add(PROPERTIES, pathId, keyId, valueId);
    }

    void writeClassOrInterfaceType(IClass c) {
        String classStr = fixTypeString(c.getName().toString());
        if (c.isInterface()) {
            _db.add(INTERFACE_TYPE, classStr);
        }
        else {
            _db.add(CLASS_TYPE, classStr);
        }
        _db.add(CLASS_HEAP, _rep.classConstant(c), classStr);

        if(c instanceof ShrikeClass) { //We have currently disabled annotations for Android
            Collection<Annotation> annotations = c.getAnnotations();
            if (annotations != null) {
                for (Annotation annotation : annotations) {
                    _db.add(CLASS_ANNOTATION, classStr, fixTypeString(annotation.getType().toString()));
                }
            }
        }
    }

    void writeDirectSuperclass(IClass sub, IClass sup) {
        _db.add(DIRECT_SUPER_CLASS, writeType(sub.getReference()), writeType(sup.getReference()));
    }

    void writeDirectSuperinterface(IClass clazz, IClass iface) {
        _db.add(DIRECT_SUPER_IFACE, writeType(clazz.getReference()), writeType(iface.getReference()));
    }

    private String writeType(IClass c) {
        // The type itself is already taken care of by writing the
        // IClass declaration, so we don't actually write the type
        // here, and just return the string.
        return fixTypeString(c.getName().toString());
    }

    private String writeType(TypeReference t) {
        String inMap = _typeMap.get(t.toString());
        String typeName;

        if(inMap == null)
        {
            typeName= fixTypeString(t.toString());
            _typeMap.put(t.toString(),typeName);
        }
        else
            typeName = inMap;

        //If its an ArrayType and it was not on the typeMap, add the appropriate facts
        if (t.isArrayType() && inMap == null) {
            _db.add(ARRAY_TYPE, typeName);
            TypeReference componentType = t.getArrayElementType();
            _db.add(COMPONENT_TYPE, typeName, writeType(componentType));
            _db.add(CLASS_HEAP, _rep.classConstant(typeName), typeName);
        }
        else if (t.isPrimitiveType() || t.isReferenceType() || t.isClassType()) {

        }
        else {
            throw new RuntimeException("Don't know what to do with type " + t);
        }

        return typeName;
    }

    void writePhantomType(TypeReference t) {
        String type = writeType(t);
        if(_phantomType.get(type) == null) {
            _db.add(PHANTOM_TYPE, type);
            _phantomType.put(type,"");
        }
    }

    void writePhantomMethod(MethodReference m) {
        String sig = _rep.signature(m);
        if(_phantomMethod.get(sig) == null) {
            //System.out.println("Method " + sig + " is phantom.");
            _phantomMethod.put(sig,"");
            _db.add(PHANTOM_METHOD, sig);
            _db.add(STRING_RAW, sig, sig);
            String arity = Integer.toString(m.getNumberOfParameters());
            _db.add(METHOD, sig, _rep.simpleName(m), _rep.params(m), writeType(m.getDeclaringClass()), writeType(m.getReturnType()), m.getDescriptor().toUnicodeString(), arity);
        }
    }

    void writePhantomBasedMethod(MethodReference m) {
        String sig = _rep.signature(m);
        //System.out.println("Method signature " + sig + " contains phantom types.");
        if(_phantomBasedMethod.get(sig) == null) {
            _phantomBasedMethod.put(sig,"");
            _db.add(PHANTOM_BASED_METHOD, sig);
        }
//        _db.add(STRING_RAW, sig, sig);
//        String arity = Integer.toString(m.getNumberOfParameters());
//        _db.add(METHOD, sig, _rep.simpleName(m), _rep.params(m), writeType(m.getDeclaringClass()), writeType(m.getReturnType()), m.getDescriptor().toUnicodeString());
    }

    void writeEnterMonitor(IMethod m, SSAMonitorInstruction instruction, Local var, Session session) {
        int index = session.calcInstructionNumber(instruction);
        String insn = _rep.instruction(m, instruction, session, index);
        String methodId = _rep.signature(m);

        _db.add(ENTER_MONITOR, insn, str(index), _rep.local(m, var), methodId);
    }

    void writeExitMonitor(IMethod m, SSAMonitorInstruction instruction, Local var, Session session) {
        int index = session.calcInstructionNumber(instruction);
        String insn = _rep.instruction(m, instruction, session, index);
        String methodId = _rep.signature(m);

        _db.add(EXIT_MONITOR, insn, str(index), _rep.local(m, var), methodId);
    }

    void writeAssignLocal(IMethod m, SSAInstruction instruction, Local to, Local from, Session session) {
        int index = session.calcInstructionNumber(instruction);
        String insn = _rep.instruction(m, instruction, session, index);
        String methodId = _rep.signature(m);

        _db.add(ASSIGN_LOCAL, insn, str(index), _rep.local(m, from), _rep.local(m, to), methodId);
    }

    void writeAssignHeapAllocation(IR ir, IMethod m, SSANewInstruction instruction, Local l, Session session) {
        String heap = _rep.heapAlloc(m, instruction, session);


        _db.add(NORMAL_HEAP, heap, writeType(instruction.getConcreteType()));

        if (instruction.getNewSite().getDeclaredType().isArrayType()) {
            int arrayLengthVar = instruction.getUse(0);
            SymbolTable symbolTable = ir.getSymbolTable();
            if (symbolTable.isIntegerConstant(arrayLengthVar)) {
                int arrayLength = symbolTable.getIntValue(arrayLengthVar);

                if(arrayLength == 0)
                    _db.add(EMPTY_ARRAY, heap);
            }
        }

        int index = session.calcInstructionNumber(instruction);
        String insn = _rep.instruction(m, instruction, session, index);
        String methodId = _rep.signature(m);
        _db.add(ASSIGN_HEAP_ALLOC, insn, str(index), heap, _rep.local(m, l), methodId, ""+getLineNumberFromInstruction(ir, instruction));
    }

    //Sifis: This information is not correct for StringConstants as we take the index of
    // the instruction these constants are used in
    private static int getLineNumberFromInstruction(IR ir, SSAInstruction instruction) {
        if(instruction.iindex == -1)
            return 0;
        IBytecodeMethod method = (IBytecodeMethod)ir.getMethod();
        int sourceLineNum;
        int bytecodeIndex;
        try {
            bytecodeIndex = method.getBytecodeIndex(instruction.iindex);
            sourceLineNum = method.getLineNumber(bytecodeIndex);
        } catch (InvalidClassFileException e) {
            sourceLineNum = 0;
        }
        return sourceLineNum;
    }

    private TypeReference getComponentType(TypeReference type) {
        // Soot calls the component type of an array type the "element
        // type", which is rather confusing, since in an array type
        // A[][][], the JVM Spec defines A to be the element type, and
        // A[][] is the component type.
        return type.getArrayElementType();
    }

    /**
     * NewMultiArray is slightly complicated because an array needs to
     * be allocated separately for every dimension of the array.
     */
    void writeAssignNewMultiArrayExpr(IR ir, IMethod m, SSANewInstruction instruction, Local l, Session session) {
        writeAssignNewMultiArrayExprHelper(ir, m, instruction, l, _rep.local(m,l), instruction.getConcreteType(), session);
    }

    private void writeAssignNewMultiArrayExprHelper(IR ir, IMethod m, SSANewInstruction instruction, Local l, String assignTo, TypeReference arrayType, Session session) {
        String heap = _rep.heapMultiArrayAlloc(m, instruction, arrayType, session);
        int index = session.calcInstructionNumber(instruction);
        String insn = _rep.instruction(m, instruction, session, index);


        String methodId = writeMethod(m);

        _db.add(NORMAL_HEAP, heap, writeType(arrayType));
        _db.add(ASSIGN_HEAP_ALLOC, insn, str(index), heap, assignTo, methodId, ""+getLineNumberFromInstruction(ir, instruction));

        TypeReference componentType = getComponentType(arrayType);
        if (componentType.isArrayType()) {
            String childAssignTo = _rep.newLocalIntermediate(m, l, session);
            writeAssignNewMultiArrayExprHelper(ir, m, instruction, l, childAssignTo, componentType, session);
            int storeInsnIndex = session.calcInstructionNumber(instruction);
            String storeInsn = _rep.instruction(m, instruction, session, storeInsnIndex);

            _db.add(STORE_ARRAY_INDEX, storeInsn, str(storeInsnIndex), childAssignTo, assignTo, methodId);
            _db.add(VAR_TYPE, childAssignTo, writeType(componentType));
            _db.add(VAR_DECLARING_METHOD, childAssignTo, methodId);
        }
    }

    // The commented-out code below is what used to be in Doop2. It is not
    // equivalent to code in old Doop. I (YS) tried to have a more compatible
    // approach for comparison purposes.
    /*
    public void writeAssignNewMultiArrayExpr(IMethod m, Stmt stmt, Local l, NewMultiArrayExpr expr, Session session) {
        // what is a normal object?
        String heap = _rep.heapAlloc(m, expr, session);

        _db.addInput("NormalObject",
                _db.asEntity(heap),
                writeType(expr.getType()));

        // local variable to assign the current array allocation to.
        String assignTo = _rep.local(m, l);

        Type type = (ArrayType) expr.getType();
        int dimensions = 0;
        while(type instanceof ArrayType)
            {
                ArrayType arrayType = (ArrayType) type;

                // make sure we store the type
                writeType(type);

                type = getComponentType(arrayType);
                dimensions++;
            }

        Type elementType = type;

        int index = session.calcInstructionNumber(stmt);
        String rep = _rep.instruction(m, stmt, session, index);

        _db.addInput("AssignMultiArrayAllocation",
                _db.asEntity(rep),
                _db.asIntColumn(str(index)),
                _db.asEntity(heap),
                _db.asIntColumn(str(dimensions)),
                _db.asEntity(assignTo),
                _db.asEntity("Method", _rep.method(m)));

    // idea: do generate the heap allocations, but not the assignments
    // (to array indices). Do store the type of those heap allocations
    }
    */

    private void writeAssignStringConstant(IR ir, IMethod m, SSAInstruction instruction, Local l, ConstantValue s, Session session) {
        int index = session.calcInstructionNumber(instruction);
        String constant = s.getValue().toString();
        String heapId = writeStringConstant(constant);

        String insn = _rep.signature(m) + "/assign/instruction" + index; // Not using _rep.instruction() because we do not want to be identified by our instr
        String methodId = _rep.signature(m);

        _db.add(ASSIGN_HEAP_ALLOC, insn, str(index), heapId, _rep.local(m, l), methodId, ""+getLineNumberFromInstruction(ir, instruction));
    }

    private void writeAssignNull(IMethod m, SSAInstruction instruction, Local l, Session session) {
        int index = session.calcInstructionNumber(instruction);
        String insn = _rep.signature(m) + "/assign/instruction" + index; // Not using _rep.instruction() because we do not want to be identified by our instr
        String methodId = _rep.signature(m);

        _db.add(ASSIGN_NULL, insn, str(index), _rep.local(m, l), methodId);
    }

    private void writeAssignNumConstant(IMethod m, SSAInstruction instruction, Local l, ConstantValue constant, Session session) {
        int index = session.calcInstructionNumber(instruction);
        String insn = _rep.signature(m) + "/assign/instruction" + index; // Not using _rep.instruction() because we do not want to be identified by our instr
        String methodId = _rep.signature(m);

        _db.add(ASSIGN_NUM_CONST, insn, str(index), constant.toString().substring(1), _rep.local(m, l), methodId);
    }

    private void writeAssignMethodTypeConstant(IMethod m, SSAInstruction instr, Local l, ConstantValue constant, Session session) {
        int index = session.calcInstructionNumber(instr);
        String insn = _rep.signature(m) + "/assign/instruction" + index; // Not using _rep.instruction() because we do not want to be identified by our instr
        //String handleName = constant.getMethodRef().toString();
        String handleName =(String) constant.getValue();
        String heap = _rep.methodTypeConstant(handleName);
        String methodId = _rep.signature(m);

        _db.add(METHOD_TYPE_CONSTANT, heap);
        _db.add(ASSIGN_HEAP_ALLOC, insn, str(index), heap, _rep.local(m, l), methodId, "0");
    }

    private void writeAssignMethodHandleConstant(IMethod m, SSAInstruction instr, Local l, ConstantValue constant, Session session) {
        int index = session.calcInstructionNumber(instr);
        String insn = _rep.signature(m) + "/assign/instruction" + index; // Not using _rep.instruction() because we do not want to be identified by our instr
        //String handleName = constant.getMethodRef().toString();
        String handleName =(String) constant.getValue();
        String heap = _rep.methodHandleConstant(handleName);
        String methodId = _rep.signature(m);

        _db.add(METHOD_HANDLE_CONSTANT, heap, handleName);
        _db.add(ASSIGN_HEAP_ALLOC, insn, str(index), heap, _rep.local(m, l), methodId, "0");
    }

    private void writeAssignClassConstant(IMethod m, SSAInstruction instruction, Local l, ConstantValue constant, Session session) {
        String s = constant.toString().substring(1);
        String heap;
        String actualType;

        TypeReference t;
        if(constant.getValue() instanceof TypeReference)
            t = (TypeReference) constant.getValue();
        else
            t = TypeReference.find(ClassLoaderReference.Primordial, s);

        if(t == null) {
            heap = "<class " + fixTypeString(s) + ">";
            actualType = fixTypeString(s);
        }
        else {
            heap = _rep.classConstant(t);
            actualType = fixTypeString(t.toString());
        }


        _db.add(CLASS_HEAP, heap, actualType);
        int index = session.calcInstructionNumber(instruction);
        String insn = _rep.signature(m) + "/assign/instruction" + index; // Not using _rep.instruction() because we do not want to be identified by our instr
        String methodId = _rep.signature(m);

        // REVIEW: the class object is not explicitly written. Is this always ok?
        _db.add(ASSIGN_HEAP_ALLOC, insn, str(index), heap, _rep.local(m, l), methodId, "0");
    }

    //Parameter is SSAInstruction because both SSAConversionInstruction and SSACheckCastInstruction are cast instructions
    void writeAssignCast(IMethod m, SSAInstruction instruction, Local to, Local from, TypeReference t, Session session) {
        int index = session.calcInstructionNumber(instruction);
        String insn = _rep.instruction(m, instruction, session, index);
        String methodId = _rep.signature(m);

        _db.add(ASSIGN_CAST, insn, str(index), _rep.local(m, from), _rep.local(m, to), writeType(t), methodId);
    }

    void writeAssignCastNumericConstant(IMethod m, SSAInstruction instruction, Local to, Local from, TypeReference t, Session session) {
        int index = session.calcInstructionNumber(instruction);
        String insn = _rep.instruction(m, instruction, session, index);
        String methodId = _rep.signature(m);

        _db.add(ASSIGN_CAST_NUM_CONST, insn, str(index), from.getValue(), _rep.local(m, to), writeType(t), methodId);
    }

    void writeAssignCastNull(IMethod m, SSAInstruction instruction, Local to, TypeReference t, Session session) {
        int index = session.calcInstructionNumber(instruction);
        String insn = _rep.instruction(m, instruction, session, index);
        String methodId = _rep.signature(m);

        _db.add(ASSIGN_CAST_NULL, insn, str(index), _rep.local(m, to), writeType(t), methodId);
    }

    void writeStoreInstanceField(IMethod m, SSAInstruction instruction, FieldReference f, Local base, Local from, Session session) {
        writeInstanceField(m, instruction, f, base, from, session, STORE_INST_FIELD);
    }

    void writeLoadInstanceField(IMethod m, SSAInstruction instruction, FieldReference f, Local base, Local to, Session session) {
        writeInstanceField(m, instruction, f, base, to, session, LOAD_INST_FIELD);
    }

    private void writeInstanceField(IMethod m, SSAInstruction instruction, FieldReference f, Local base, Local var, Session session, PredicateFile predicateFile) {
        int index = session.calcInstructionNumber(instruction);
        String insn = _rep.instruction(m, instruction, session, index);
        String methodId = _rep.signature(m);

        TypeReference declaringClass = getCorrectFieldDeclaringClass(f, m.getClassHierarchy());
        String fieldId = _rep.signature(f, declaringClass);
        _db.add(predicateFile, insn, str(index), _rep.local(m, var), _rep.local(m, base), fieldId, methodId);
    }

    void writeStoreStaticField(IMethod m, SSAInstruction instruction, FieldReference f, Local from, Session session) {
        writeStaticField(m, instruction, f, from, session, STORE_STATIC_FIELD);
    }

    void writeLoadStaticField(IMethod m, SSAInstruction instruction, FieldReference f, Local to, Session session) {
        writeStaticField(m, instruction, f, to, session, LOAD_STATIC_FIELD);
    }

    private void writeStaticField(IMethod m, SSAInstruction stmt, FieldReference f, Local var, Session session, PredicateFile predicateFile) {
        int index = session.calcInstructionNumber(stmt);
        String insn = _rep.instruction(m, stmt, session, index);
        String methodId = _rep.signature(m);

        TypeReference declaringClass = getCorrectFieldDeclaringClass(f, m.getClassHierarchy());
        String fieldId = _rep.signature(f, declaringClass);
        _db.add(predicateFile, insn, str(index), _rep.local(m, var), fieldId, methodId);
    }

    void writeLoadArrayIndex(IMethod m, SSAInstruction  instruction, Local base, Local to, Local arrIndex, Session session) {
        writeFieldOrIndex(m, instruction, base, to, arrIndex, session, LOAD_ARRAY_INDEX);
    }

    void writeStoreArrayIndex(IMethod m, SSAInstruction instruction, Local base, Local from, Local arrIndex, Session session) {
        writeFieldOrIndex(m, instruction, base, from, arrIndex, session, STORE_ARRAY_INDEX);
    }

    private void writeFieldOrIndex(IMethod m, SSAInstruction instruction, Local base, Local var, Local arrIndex, Session session, PredicateFile predicateFile) {
        int index = session.calcInstructionNumber(instruction);
        String insn = _rep.instruction(m, instruction, session, index);
        String methodId = _rep.signature(m);

        _db.add(predicateFile, insn, str(index), _rep.local(m, var), _rep.local(m, base), methodId);

        if (arrIndex != null)
            _db.add(ARRAY_INSN_INDEX, insn, _rep.local(m, arrIndex));
    }

    void writeApplicationClass(IClass application) {
        _db.add(APP_CLASS, writeType(application.getReference()));
    }

    //To be used instead of IClass.getAllFields()to avoid NullPointerExceptions in Android
    static Collection <IField> getAllFieldsOfClass(IClass cl)
    {
        Collection <IField> result = new LinkedList<IField>();
        result.addAll(cl.getAllInstanceFields());

        IClass s = cl;
        while (s != null) {
            try {
                Collection<IField> flds = s.getDeclaredStaticFields();
                result.addAll(flds);
            }catch (NullPointerException exc){
                ;
            }

            s = s.getSuperclass();
        }
        if(cl.getAllImplementedInterfaces() != null) {
            for (IClass interf : cl.getAllImplementedInterfaces()) {
                try {
                    Collection<IField> flds = interf.getDeclaredStaticFields();
                    result.addAll(flds);
                } catch (NullPointerException exc) {
                    ;
                }
            }
        }
        return result;
    }

    private TypeReference getCorrectFieldDeclaringClass(FieldReference f, IClassHierarchy cha)
    {
        IClass targetClass = cha.lookupClass(f.getDeclaringClass());
        TypeReference typeRef = f.getDeclaringClass();
        if(targetClass == null) {
            //System.out.println("Failed to find class: " + fixTypeString(f.getDeclaringClass().getName().toString()) + " in class hierarchy.");
            writePhantomType(f.getDeclaringClass());
        }
        else
        {
            //for(IField field: targetClass.getAllFields())
            //We use our own method instead of IClass.getAllFields() because sometimes getAllFields() throws NullPointerException.
            for(IField field: getAllFieldsOfClass(targetClass))
            {
                if(field.getName().toString().equals(f.getName().toString()))
                {
                    typeRef = field.getDeclaringClass().getReference();
                    break;
                }
            }
        }
        return typeRef;
    }

    public String writeField(IField f) {
        String fieldId = _rep.signature(f);
        _db.add(FIELD_SIGNATURE, fieldId, writeType(f.getReference().getDeclaringClass()), _rep.simpleName(f), writeType(f.getFieldTypeReference()));
        if(f instanceof FieldImpl) { //Currently annotations do not work on android and are disabled
            Collection<Annotation> annotations = f.getAnnotations();
            if (annotations != null) {
                for (Annotation annotation : annotations) {
                    _db.add(FIELD_ANNOTATION, fieldId, fixTypeString(annotation.getType().toString()));
                }
            }
        }
        return fieldId;
    }


    void writeFieldModifier(IField f, String modifier) {
        String fieldId = _rep.signature(f);
        _db.add(FIELD_MODIFIER, modifier, fieldId);
    }

    void writeClassModifier(IClass c, String modifier) {
        String type = fixTypeString(c.getName().toString());
        _db.add(CLASS_MODIFIER, modifier, type);
    }

    void writeMethodModifier(IMethod m, String modifier) {
        String methodId = _rep.signature(m);
        _db.add(METHOD_MODIFIER, modifier, methodId);
    }


    void writeReturn(IMethod m, SSAInstruction instruction, Local l, Session session) {
        int index = session.calcInstructionNumber(instruction);
        String insn = _rep.instruction(m, instruction, session, index);
        String methodId = _rep.signature(m);

        _db.add(RETURN, insn, str(index), _rep.local(m, l), methodId);
    }

    void writeReturnVoid(IMethod m, SSAInstruction instruction, Session session) {
        int index = session.calcInstructionNumber(instruction);
        String insn = _rep.instruction(m, instruction, session, index);
        String methodId = _rep.signature(m);

        _db.add(RETURN_VOID, insn, str(index), methodId);
    }

    // The return var of native methods is exceptional, in that it does not
    // correspond to a return instruction.
    void writeNativeReturnVar(IMethod m) {
        String methodId = _rep.signature(m);

        String  var = _rep.nativeReturnVar(m);
        _db.add(NATIVE_RETURN_VAR, var, methodId);
        _db.add(VAR_TYPE, var, writeType(m.getReturnType()));
        _db.add(VAR_DECLARING_METHOD, var, methodId);
    }

    void writeGoto(IMethod m, SSAGotoInstruction instruction, SSAInstruction to, Session session) {
        int index = session.getInstructionNumber(instruction);
        int indexTo = session.getInstructionNumber(to);
        String insn = _rep.instruction(m, instruction, session, index);
        String methodId = _rep.signature(m);

        _db.add(GOTO, insn, str(index), str(indexTo), methodId);
    }

    /**
     * If
     */
    void writeIf(IMethod m, SSAConditionalBranchInstruction instruction, Local var1, Local var2, SSAInstruction to, Session session) {
        // index was already computed earlier
        int index = session.getMaxInstructionNumber(instruction);
        int indexTo = session.getInstructionNumber(to);
        String insn = _rep.instruction(m, instruction, session, index);
        String methodId = _rep.signature(m);

        _db.add(IF, insn, str(index), str(indexTo), methodId);
        _db.add(IF_VAR, insn, _rep.local(m, var1));
        _db.add(IF_VAR, insn, _rep.local(m, var2));
    }
//
//    void writeTableSwitch(IMethod inMethod, TableSwitchStmt stmt, Session session) {
//        int stmtIndex = session.getUnitNumber(stmt);
//
//        Value v = writeImmediate(inMethod, stmt, stmt.getKey(), session);
//
//        if(!(v instanceof Local))
//            throw new RuntimeException("Unexpected key for TableSwitch statement " + v + " " + v.getClass());
//
//        Local l = (Local) v;
//        String insn = _rep.instruction(inMethod, stmt, session, stmtIndex);
//        String methodId = writeMethod(inMethod);
//
//        _db.add(TABLE_SWITCH, insn, str(stmtIndex), _rep.local(inMethod, l), methodId);
//
//        for (int tgIndex = stmt.getLowIndex(), i = 0; tgIndex <= stmt.getHighIndex(); tgIndex++, i++) {
//            session.calcInstructionNumber(stmt.getTarget(i));
//            int indexTo = session.getUnitNumber(stmt.getTarget(i));
//
//            _db.add(TABLE_SWITCH_TARGET, insn, str(tgIndex), str(indexTo));
//        }
//
//        session.calcInstructionNumber(stmt.getDefaultTarget());
//        int defaultIndex = session.getUnitNumber(stmt.getDefaultTarget());
//
//        _db.add(TABLE_SWITCH_DEFAULT, insn, str(defaultIndex));
//    }


    void writeLookupSwitch(IR ir,IMethod inMethod, SSASwitchInstruction instruction, Session session, Local switchVar) {
//        if(inMethod instanceof DexIMethod) //Currently disabled for android
//            return;
        int instrIndex = session.getInstructionNumber(instruction);
        int targetIndex, targetWALAIndex;
        int defaultIndex, defaultWALAIndex;
        IBytecodeMethod bm = (IBytecodeMethod) inMethod;
        //Value v = writeImmediate(inMethod, instruction, instruction.getUse(0), session);
        String insn = _rep.instruction(inMethod, instruction, session, instrIndex);
        String methodId = _rep.signature(inMethod);

        _db.add(LOOKUP_SWITCH, insn, str(instrIndex), _rep.local(inMethod, switchVar), methodId);

        int casesAndLabels[] = instruction.getCasesAndLabels();
        SSAInstruction instructions[] = ir.getInstructions();
        for(int i = 0; i < casesAndLabels.length; i+=2) {
            int tgIndex = casesAndLabels[i];
            //session.calcInstructionNumber(instructions[casesAndLabels[i+1]]);
            targetWALAIndex = casesAndLabels[i+1];
            if(inMethod instanceof DexIMethod) {
                try {
                    targetWALAIndex = bm.getInstructionIndex(targetWALAIndex);
                } catch (InvalidClassFileException e) {
                    e.printStackTrace();
                }
            }
            if(instructions[targetWALAIndex] == null)
            {
                targetWALAIndex = getNextNonNullInstruction(ir,targetWALAIndex);
                if(targetWALAIndex == -1)
                    logger.error("Error: Next non-null instruction index = -1");
            }
            targetIndex = session.getInstructionNumber(instructions[targetWALAIndex]);

            _db.add(LOOKUP_SWITCH_TARGET, insn, str(tgIndex), str(targetIndex));
        }

        defaultWALAIndex = instruction.getDefault();
        if(inMethod instanceof DexIMethod) {
            try {
                defaultWALAIndex = bm.getInstructionIndex(defaultWALAIndex);
            } catch (InvalidClassFileException e) {
                e.printStackTrace();
            }
        }
        if(instructions[defaultWALAIndex] == null)
        {
            defaultWALAIndex = getNextNonNullInstruction(ir,defaultWALAIndex);
            if(defaultWALAIndex == -1)
                logger.error("Error: Next non-null instruction index = -1");
        }
        //session.calcInstructionNumber(instructions[defaultWALAIndex]);
        defaultIndex = session.getInstructionNumber(instructions[defaultWALAIndex]);


        _db.add(LOOKUP_SWITCH_DEFAULT, insn, str(defaultIndex));
    }

    void writeUnsupported(IMethod m, IR ir, SSAInstruction instruction, Session session) {
        int index = session.calcInstructionNumber(instruction);
        String insn = _rep.unsupported(m, ir, instruction, index);
        String methodId = _rep.signature(m);

        _db.add(UNSUPPORTED_INSTRUCTION, insn, str(index), methodId);
    }

    /**
     * Throw statement
     */
    void writeThrow(IMethod m, SSAThrowInstruction instruction, Local l, Session session) {
        int index = session.calcInstructionNumber(instruction);
        String insn = _rep.throwLocal(m, l, session);
        String methodId = _rep.signature(m);

        _db.add(THROW, insn, str(index), _rep.local(m, l), methodId);
    }

    /**
     * Throw null
     */
    void writeThrowNull(IMethod m, SSAThrowInstruction instruction, Session session) {
        int index = session.calcInstructionNumber(instruction);
        String insn = _rep.instruction(m, instruction, session, index);
        String methodId = _rep.signature(m);

        _db.add(THROW_NULL, insn, str(index), methodId);
    }

    void writeExceptionHandlerPrevious(IMethod m, SSACFG.ExceptionHandlerBasicBlock current, SSACFG.ExceptionHandlerBasicBlock previous, Session session) {
        TypeReference prevType = null;
        SSAGetCaughtExceptionInstruction prevCatch = previous.getCatchInstruction();
        Iterator<TypeReference> prevTypes = previous.getCaughtExceptionTypes();
        int prevNumOfScopes = _rep.getHandlerNumOfScopes(m,prevCatch);
        while(prevTypes.hasNext())
            prevType = prevTypes.next();

        SSAGetCaughtExceptionInstruction currCatch = current.getCatchInstruction();
        TypeReference currType = current.getCaughtExceptionTypes().next();

        _db.add(EXCEPT_HANDLER_PREV, _rep.handler(m, currCatch, currType, session,0), _rep.handler(m, prevCatch, prevType, session, prevNumOfScopes));
    }

    void writeExceptionHandler(IR ir, IMethod m, SSACFG.ExceptionHandlerBasicBlock handlerBlock, Session session, TypeInference typeInference, WalaExceptionHelper exceptionHelper) {

        SSAGetCaughtExceptionInstruction catchInstr = handlerBlock.getCatchInstruction();
        if(catchInstr == null)
        {
            return;
        }

        int handlerIndex = session.getInstructionNumber(catchInstr);
        Local caught = createLocal(ir, catchInstr, catchInstr.getDef(),typeInference);

        SSAInstruction[] instructions = ir.getInstructions();
        SSAInstruction startInstr = null;
        SSAInstruction endInstr = null;

        Integer[] scopeArray = exceptionHelper.computeScopeForExceptionHandler(handlerBlock.getFirstInstructionIndex());
        if(scopeArray.length == 0)
        {
            System.out.println("ScopeArray has 0 length :(. Handler " + handlerBlock.getFirstInstructionIndex());
        }


        String prev = null;
        for(int i=0; i < scopeArray.length; i+=2) {
            startInstr = instructions[scopeArray[i]];
            endInstr = instructions[scopeArray[i + 1]];
            int beginIndex = session.getInstructionNumber(startInstr);
            int endIndex = session.getMaxInstructionNumber(endInstr);
            Iterator<TypeReference> excTypes = handlerBlock.getCaughtExceptionTypes();
//            if(m.getName().toString().equals("initialize") &&
//                    m.getDeclaringClass().getName().toString().contains("Lokhttp3/internal/cache/DiskLruCache"))
//                System.out.println("WALA " + handlerBlock.getFirstInstructionIndex() +" ("+
//                        scopeArray[i] + " - " + scopeArray[i + 1] + ") DOOP "+
//                        handlerIndex +" ("+ beginIndex + " - " + endIndex + ")");
            while (excTypes.hasNext()) {
                TypeReference excType = excTypes.next();
                String insn = _rep.handler(m, catchInstr, excType, session, i/2);
                _db.add(EXCEPTION_HANDLER, insn, _rep.signature(m), str(handlerIndex), fixTypeString(excType.getName().toString()), _rep.local(m, caught), str(beginIndex), str(endIndex));
                if (prev != null)
                    _db.add(EXCEPT_HANDLER_PREV, insn, prev);
                prev = insn;
            }
        }
        if(scopeArray.length > 2)
            _rep.putHandlerNumOfScopes(m, catchInstr,(scopeArray.length - 1)/2 );
    }

    void writeThisVar(IMethod m) {
        String methodId = _rep.signature(m);
        String thisVar = _rep.thisVar(m);
        _db.add(THIS_VAR, methodId, thisVar);
        _db.add(VAR_TYPE, thisVar, writeType(m.getReference().getDeclaringClass()));
        _db.add(VAR_DECLARING_METHOD, thisVar, methodId);
    }

    void writeMethodDeclaresException(IMethod m, TypeReference exception) {
        _db.add(METHOD_DECL_EXCEPTION, writeType(exception), _rep.signature(m));
    }

    void writeFormalParam(IMethod m, int paramIndex, int actualIndex) {
        String methodId = _rep.signature(m);
        String var = _rep.param(m, paramIndex);
        _db.add(FORMAL_PARAM, str(actualIndex), methodId, var);
        _db.add(VAR_TYPE, var, writeType(m.getParameterType(paramIndex)));
        _db.add(VAR_DECLARING_METHOD, var, methodId);
    }

    void writeLocal(IMethod m, Local l) {
        String local = _rep.local(m, l);

        _db.add(VAR_TYPE, local, writeType(l.getType()));
        _db.add(VAR_DECLARING_METHOD, local, _rep.signature(m));
    }

    void writeStringConstantExpression(IR ir, IMethod inMethod, SSAInstruction instruction, Local l, ConstantValue constant, Session session) {
        // introduce a new temporary variable
        writeLocal(inMethod, l);
        writeAssignStringConstant(ir, inMethod, instruction, l, constant, session);
    }

    void writeNullExpression(IMethod inMethod, SSAInstruction instruction, Local l, Session session) {
        // introduce a new temporary variable
        writeLocal(inMethod, l);
        writeAssignNull(inMethod, instruction, l,session);
    }

    void writeNumConstantExpression(IMethod inMethod, SSAInstruction instruction, Local l, ConstantValue constant, Session session) {
        // introduce a new temporary variable
        writeLocal(inMethod, l);
        writeAssignNumConstant(inMethod, instruction, l, constant, session);
    }

    void writeClassConstantExpression(IMethod inMethod, SSAInstruction instruction, Local l, ConstantValue constant, Session session) {
        // introduce a new temporary variable
        writeLocal(inMethod, l);
        writeAssignClassConstant(inMethod, instruction, l, constant, session);
    }

    private Local writeMethodHandleConstantExpression(IMethod inMethod, SSAInstruction instruction, Local l, ConstantValue constant, Session session) {
        // introduce a new temporary variable
//        String basename = "$mhandleconstant";
//        String varname = basename + session.nextNumber(basename);
//        Local l = new Local(varname,-1, TypeReference.JavaLangInvokeMethodHandle);
        writeLocal(inMethod, l);
        writeAssignMethodHandleConstant(inMethod, instruction, l, constant, session);
        return l;
    }

    private Local writeMethodTypeConstantExpression(IMethod inMethod, SSAInstruction instruction, Local l, ConstantValue constant, Session session) {
        // introduce a new temporary variable
//        String basename = "$mhandleconstant";
//        String varname = basename + session.nextNumber(basename);
//        Local l = new Local(varname,-1, TypeReference.JavaLangInvokeMethodHandle);
        writeLocal(inMethod, l);
        writeAssignMethodTypeConstant(inMethod, instruction, l, constant, session);
        return l;
    }


    private void writeActualParams(IMethod inMethod, IR ir, SSAInvokeInstruction instruction, String invokeExprRepr, Session session, TypeInference typeInference) {
        if (instruction.isStatic()) {
            //for (int i = 0; i < instruction.getNumberOfParameters(); i++) {
            for (int i = 0; i < instruction.getNumberOfPositionalParameters(); i++) {
                Local l = createLocal(ir, instruction, instruction.getUse(i), typeInference);
                _db.add(ACTUAL_PARAMETER, str(i), invokeExprRepr, _rep.local(inMethod, l));
            }
        }
        else {
            //for (int i = 1; i < instruction.getNumberOfParameters(); i++) {
            for (int i = 1; i < instruction.getNumberOfPositionalParameters(); i++) {
                Local l = createLocal(ir, instruction, instruction.getUse(i), typeInference);
                _db.add(ACTUAL_PARAMETER, str(i-1), invokeExprRepr, _rep.local(inMethod, l));
            }

        }
        if (instruction instanceof SSAInvokeDynamicInstruction) {
            for (int j = 0; j < ((SSAInvokeDynamicInstruction) instruction).getBootstrap().callArgumentCount(); j++) {
                int arg = ((SSAInvokeDynamicInstruction) instruction).getBootstrap().callArgumentIndex(j);

                //Local l = createLocal(ir, instruction, arg); //TODO: TypeInference for bootstrap parameters??
                Local l = bootstrapParamHelper(ir, inMethod, (SSAInvokeDynamicInstruction)instruction, j, session);
                _db.add(BOOTSTRAP_PARAMETER, str(j), invokeExprRepr, _rep.local(inMethod, l));

            }
        }
    }

    private Local bootstrapParamHelper(IR ir, IMethod m, SSAInvokeDynamicInstruction dynamicInvoke, int argNum, Session session)
    {
        Local param = null;
        BootstrapMethodsReader.BootstrapMethod bootstrapMethod = dynamicInvoke.getBootstrap();
        //Object argObj = bootstrapMethod.callArgument();
        TypeReference argType = TypeReference.JavaLangObject;
        String basename = "";
        int argumentKind = bootstrapMethod.callArgumentKind(argNum);
        ConstantPoolParser constantPool = bootstrapMethod.getCP();
        int index = bootstrapMethod.callArgumentIndex(argNum);
        String argValue = "<NOVALUE>";
        try {
            if (argumentKind == ClassConstants.CONSTANT_Utf8 || argumentKind == ClassConstants.CONSTANT_String) {
                argType = TypeReference.JavaLangString;
                basename = "$stringconstant";
                if (argumentKind == ClassConstants.CONSTANT_Utf8)
                    argValue = constantPool.getCPUtf8(index);
                else
                    argValue = constantPool.getCPString(index);
            } else if (argumentKind == ClassConstants.CONSTANT_Class) {
                argType = TypeReference.JavaLangClass;
                basename = "$classconstant";
                argValue = constantPool.getCPClass(index);
            } else if (argumentKind == ClassConstants.CONSTANT_Integer) {
                argType = TypeReference.JavaLangInteger;
                basename = "$numconstant";
                argValue = Integer.toString(constantPool.getCPInt(index));
            } else if (argumentKind == ClassConstants.CONSTANT_Float) {
                argType = TypeReference.JavaLangFloat;
                basename = "$numconstant";
                argValue = Float.toString(constantPool.getCPFloat(index));
            } else if (argumentKind == ClassConstants.CONSTANT_Double) {
                argType = TypeReference.JavaLangDouble;
                basename = "$numconstant";
                argValue = Double.toString(constantPool.getCPDouble(index));
            } else if (argumentKind == ClassConstants.CONSTANT_Long) {
                argType = TypeReference.JavaLangLong;
                basename = "$numconstant";
                argValue = Long.toString(constantPool.getCPLong(index));
            } else if (argumentKind == ClassConstants.CONSTANT_MethodHandle) {
                argType = TypeReference.JavaLangInvokeMethodHandle;
                basename = "$mhandleconstant";
                argValue ="<" + constantPool.getCPHandleClass(index).replace('/','.') + ": ";
                //argValue +=constantPool.getCPHandleType(index) + " " + constantPool.getCPHandleName(index) +"()>" ;
                argValue += createMethodSignature(constantPool.getCPHandleType(index), constantPool.getCPHandleName(index)) +">";
            } else if (argumentKind == ClassConstants.CONSTANT_MethodType) {
                argType = TypeReference.JavaLangInvokeMethodType;
                basename = "$mtypeconstant";
                argValue = constantPool.getCPMethodType(index);
            }
        }catch(InvalidClassFileException exc)
        {
            System.err.println("InvalidClassFile ");
        }
        ConstantValue val = new ConstantValue(argValue);
        String varname = basename + session.nextNumber(basename);
        Local l = new Local (varname, -1, argType);
        if (argumentKind == ClassConstants.CONSTANT_Utf8 || argumentKind == ClassConstants.CONSTANT_String) {
            this.writeStringConstantExpression(ir, m, dynamicInvoke, l, val, session);
        }else if (argumentKind == ClassConstants.CONSTANT_Integer || argumentKind == ClassConstants.CONSTANT_Float
                || argumentKind == ClassConstants.CONSTANT_Double || argumentKind == ClassConstants.CONSTANT_Long) {
            this.writeNumConstantExpression(m, dynamicInvoke, l, val, session);
        }else if (argumentKind == ClassConstants.CONSTANT_Class) {
            System.out.println("CLASSCONST" + val.getValue());
            this.writeClassConstantExpression(m, dynamicInvoke, l, val, session);
        }else if (argumentKind == ClassConstants.CONSTANT_MethodHandle) {
            this.writeMethodHandleConstantExpression(m, dynamicInvoke, l, val, session);
        }else if (argumentKind == ClassConstants.CONSTANT_MethodType) {
            this.writeMethodTypeConstantExpression(m, dynamicInvoke, l, val, session);
        }


        return l;
    }

    void writeInvoke(IMethod inMethod, IR ir, SSAInvokeInstruction instruction, Local to, Session session, TypeInference typeInference) {
        String insn = writeInvokeHelper(inMethod, ir, instruction, session, typeInference);
        if(to != null)
            _db.add(ASSIGN_RETURN_VALUE, insn, _rep.local(inMethod, to));
    }

    private String writeInvokeHelper(IMethod inMethod, IR ir, SSAInvokeInstruction instruction, Session session, TypeInference typeInference) {
        String methodId = _rep.signature(inMethod);

        IBytecodeMethod method = (IBytecodeMethod)ir.getMethod();
        int sourceLineNum;
        int bytecodeIndex = 0;
        try {
            bytecodeIndex = method.getBytecodeIndex(instruction.iindex);
            sourceLineNum = method.getLineNumber(bytecodeIndex);
        } catch (InvalidClassFileException e) {
            sourceLineNum = -1;
        }

        IClassHierarchy cha = inMethod.getClassHierarchy();
        MethodReference targetRef = instruction.getCallSite().getDeclaredTarget();
        IClass targetClass = cha.lookupClass(targetRef.getDeclaringClass());

        if(targetClass == null) {
            //System.out.println("Failed to find class: " + fixTypeString(targetRef.getDeclaringClass().getName().toString()) + " in class chierarchy.");
            writePhantomType(targetRef.getDeclaringClass());
            writePhantomMethod(targetRef);
        }
        else if( targetClass.isArrayClass())
        {
            for(IMethod meth: targetClass.getAllMethods()) {
                if (meth.getName().toString().equals(targetRef.getName().toString())
                        && meth.getDescriptor().toString().equals(targetRef.getDescriptor().toString()))
                {
                    targetRef = meth.getReference();
                    break;
                }
            }
        }
        else {
            boolean foundAtFirst = false;
            for(IMethod meth: targetClass.getAllMethods()) {
                if (meth.getName().toString().equals(targetRef.getName().toString())
                        && meth.getDescriptor().toString().equals(targetRef.getDescriptor().toString()))
                {
                    targetRef = meth.getReference();
                    foundAtFirst = true;
                    break;
                }
            }
            if(!foundAtFirst) {
                String methRepr = fixTypeString(targetClass.getName().toString()) + ":" + targetRef.getName();
                if(_signaturePolyMorphicMethods.get(methRepr) != null)
                {
                    addFactsForSignaturePolymorphic(targetRef, _signaturePolyMorphicMethods.get(methRepr));
                }
                if(targetClass.isAbstract() && ! targetClass.isInterface())
                {
                    Queue<IClass> classQueue = new LinkedList<>();
                    classQueue.addAll(targetClass.getDirectInterfaces());
                    if(!classQueue.isEmpty())
                    {
                        boolean found = false;
                        IClass currClass;
                        while (!classQueue.isEmpty() && !found) {
                            currClass = classQueue.remove();
                            for(IMethod meth: currClass.getDeclaredMethods()) {
                                if (meth.getName().toString().equals(targetRef.getName().toString())
                                        && meth.getDescriptor().toString().equals(targetRef.getDescriptor().toString()))
                                {
                                    targetRef = meth.getReference();
                                    found = true;
                                    break;
                                }
                            }
                            classQueue.addAll(currClass.getDirectInterfaces());
                        }
                        if(!found)
                            writePhantomMethod(targetRef);
                    }
                }
                else
                    writePhantomMethod(targetRef);
            }
        }


        String insn = _rep.invoke(ir,inMethod, instruction, targetRef, session, typeInference);
        writeActualParams(inMethod, ir, instruction, insn, session,typeInference);

        int index = session.calcInstructionNumber(instruction);

        if(sourceLineNum != -1)
            _db.add(METHOD_INV_LINE, insn, str(sourceLineNum));

        if (instruction instanceof SSAInvokeDynamicInstruction) { //Had to put these first because wala considers them static
//            MethodReference dynInfo = instruction.getDeclaredTarget();
            MethodReference dynInfo = targetRef;
            String dynArity = String.valueOf(dynInfo.getNumberOfParameters());

            StringBuilder parameterTypes = new StringBuilder();
            for (int i = 0; i < dynInfo.getNumberOfParameters(); i++) {
                if (i==0) {
                    parameterTypes.append(fixTypeString(dynInfo.getParameterType(i).toString()));
                }
                else {
                    parameterTypes.append(", ").append(fixTypeString(dynInfo.getParameterType(i).toString()));
                }
            }
            String sig = getBootstrapSig(((SSAInvokeDynamicInstruction) instruction).getBootstrap(),inMethod.getClassHierarchy());
            _db.add(DYNAMIC_METHOD_INV, insn, str(index), sig, dynInfo.getName().toString(), fixTypeString(dynInfo.getReturnType().toString()), dynArity, parameterTypes.toString(), methodId);
        }
        else if (instruction.isStatic()) {
            _db.add(STATIC_METHOD_INV, insn, str(index), _rep.signature(targetRef), methodId);
            //_db.add(STATIC_METHOD_INV, insn, _rep.signature(targetRef), methodId);
        }
        else if (instruction.isDispatch()) {
            Local l = createLocal(ir, instruction, instruction.getReceiver(),typeInference);
            _db.add(VIRTUAL_METHOD_INV, insn, str(index), _rep.signature(targetRef), _rep.local(inMethod, l), methodId);
            //_db.add(VIRTUAL_METHOD_INV, insn, _rep.signature(targetRef), methodId);
        }
        else if (instruction.isSpecial()) {
            Local l = createLocal(ir, instruction, instruction.getReceiver(),typeInference);
            _db.add(SPECIAL_METHOD_INV, insn, str(index), _rep.signature(targetRef), _rep.local(inMethod, l), methodId);
            //_db.add(SPECIAL_METHOD_INV, insn, _rep.signature(targetRef), methodId);
        }
        else {
            throw new RuntimeException("Cannot handle invoke instruction: " + instruction);
        }

        return insn;
    }

    private void addFactsForSignaturePolymorphic(MethodReference m, List<String> declaredExceptions)
    {
        String sig = _rep.signature(m);
        _db.add(STRING_RAW, sig, sig);
        String arity = Integer.toString(m.getNumberOfParameters());
        _db.add(METHOD, sig, _rep.simpleName(m), _rep.params(m), writeType(m.getDeclaringClass()), writeType(m.getReturnType()), m.getDescriptor().toUnicodeString(), arity);
        //addMockExceptionThrows(m, declaredExceptions);
    }

    void addMockExceptionThrows(MethodReference mr, List<String> declaredExceptions)
    {
        int i = -2;
        String varBase = "mockExc";
        String methodSig = _rep.signature(mr);
        String var;
        String heap;
        String newInstr;
        String specInvInstr;
        String throwInstr;
        String targetRef;

        for(String declaredExc : declaredExceptions)
        {
            i+=3;
            var = methodSig + "/" + varBase + Integer.toString(i);
            _db.add(VAR_TYPE, var, declaredExc);
            _db.add(VAR_DECLARING_METHOD, var, methodSig);
            heap = methodSig + "/new " + declaredExc + "/0";
            _db.add(NORMAL_HEAP, heap, declaredExc);
            newInstr = methodSig + "/assign/instruction" + str(i);
            _db.add(ASSIGN_HEAP_ALLOC, newInstr, str(i), heap, var, methodSig, "0");
            specInvInstr = methodSig +"/" + declaredExc +".<init>/0" ;
            targetRef = "<" + declaredExc + ":  void <init>()>";
            _db.add(SPECIAL_METHOD_INV, specInvInstr, str(i+1), targetRef, var, methodSig);
            throwInstr = methodSig + "/throw " +varBase + Integer.toString(i) + "/0";
            _db.add(THROW, throwInstr, str(i+2),var, methodSig);
        }
    }

    private String getBootstrapSig(BootstrapMethodsReader.BootstrapMethod bootstrapMeth, IClassHierarchy cha) {
//        if (cha.lookupClass()) {
//            String bootstrapSig = bootstrapMeth.toString();
//            System.out.println("Bootstrap method is phantom: " + bootstrapSig);
//            _db.add(PHANTOM_METHOD, bootstrapSig);
//            return bootstrapSig;
//        } else
//            return _rep.signature(bootstrapMeth.resolve());
        String declaringClass = bootstrapMeth.methodClass().replace('/','.');
        StringBuilder bootStrapSig = new StringBuilder("<");
        bootStrapSig.append(declaringClass);
        bootStrapSig.append(": ");
        bootStrapSig.append(createMethodSignature(bootstrapMeth.methodType(),bootstrapMeth.methodName()));
        bootStrapSig.append(">");


        //System.out.println("\n\n\n\n\n\nBOOTSTRAP SIG " + bootStrapSig);
        return bootStrapSig.toString();
    }

    //        private Value writeImmediate(IMethod inMethod, Stmt stmt, Value v, Session session) {
////        if (v instanceof StringConstant)
////            v = writeStringConstantExpression(inMethod, stmt, (StringConstant) v, session);
////        else if (v instanceof ClassConstant)
////            v = writeClassConstantExpression(inMethod, stmt, (ClassConstant) v, session);
////        else if (v instanceof NumericConstant)
////            v = writeNumConstantExpression(inMethod, stmt, (NumericConstant) v, session);
//
//        return v;
//    }
//
    void writeAssignComparison(IMethod m, SSAComparisonInstruction instruction, Local left, Local op1, Local op2, Session session) {
        int index = session.calcInstructionNumber(instruction);
        String insn = _rep.instruction(m, instruction, session, index);
        String methodId = _rep.signature(m);

        _db.add(ASSIGN_BINOP, insn, str(index), _rep.local(m, left), methodId);

        _db.add(ASSIGN_OPER_FROM, insn, _rep.local(m, op1));
        _db.add(ASSIGN_OPER_FROM, insn, _rep.local(m, op2));

    }

    void writeAssignBinop(IMethod m, SSABinaryOpInstruction instruction, Local left, Local op1, Local op2, Session session) {
        int index = session.calcInstructionNumber(instruction);
        String insn = _rep.instruction(m, instruction, session, index);
        String methodId = _rep.signature(m);

        _db.add(ASSIGN_BINOP, insn, str(index), _rep.local(m, left), methodId);

        _db.add(ASSIGN_OPER_FROM, insn, _rep.local(m, op1));
        _db.add(ASSIGN_OPER_FROM, insn, _rep.local(m, op2));

    }
    //
    void writeAssignUnop(IMethod m, SSAUnaryOpInstruction instruction, Local to, Local from, Session session) {
        int index = session.calcInstructionNumber(instruction);
        String insn = _rep.instruction(m, instruction, session, index);
        String methodId = _rep.signature(m);

        _db.add(ASSIGN_UNOP, insn, str(index), _rep.local(m, to), methodId);

        _db.add(ASSIGN_OPER_FROM, insn, _rep.local(m, from));
    }

    void writeAssignArrayLength(IMethod m, SSAArrayLengthInstruction instruction, Local to, Local from, Session session) {
        int index = session.calcInstructionNumber(instruction);
        String insn = _rep.instruction(m, instruction, session, index);
        String methodId = _rep.signature(m);

        _db.add(ASSIGN_UNOP, insn, str(index), _rep.local(m, to), methodId);
        _db.add(ASSIGN_OPER_FROM, insn, _rep.local(m, from));
    }

    void writeAssignInstanceOf(IMethod m, SSAInstanceofInstruction instruction, Local to, Local from, TypeReference t, Session session) {
        int index = session.calcInstructionNumber(instruction);
        String insn = _rep.instruction(m, instruction, session, index);
        String methodId = _rep.signature(m);

        _db.add(ASSIGN_INSTANCE_OF, insn, str(index), _rep.local(m, from), _rep.local(m, to), writeType(t), methodId);
    }

//    void writeAssignPhantomInvoke(IMethod m, Stmt stmt, Session session) {
////        int index = session.calcInstructionNumber(stmt);
////        String insn = _rep.instruction(m, stmt, session, index);
////        String methodId = writeMethod(m);
////
////        _db.add(ASSIGN_PHANTOM_INVOKE, insn, str(index), methodId);
//    }
//
//    void writeBreakpointStmt(IMethod m, Stmt stmt, Session session) {
////        int index = session.calcInstructionNumber(stmt);
////        String insn = _rep.instruction(m, stmt, session, index);
////        String methodId = writeMethod(m);
////
////        _db.add(BREAKPOINT_STMT, insn, str(index), methodId);
//    }

    public void writeApplication(String applicationName) { _db.add(ANDROID_APPLICATION, applicationName); }

    public void writeActivity(String activity) {
        _db.add(ACTIVITY, activity);
    }

    public void writeService(String service) {
        _db.add(SERVICE, service);
    }

    public void writeContentProvider(String contentProvider) {
        _db.add(CONTENT_PROVIDER, contentProvider);
    }

    public void writeBroadcastReceiver(String broadcastReceiver) {
        _db.add(BROADCAST_RECEIVER, broadcastReceiver);
    }

    public void writeCallbackMethod(String callbackMethod) {
        _db.add(CALLBACK_METHOD, callbackMethod);
    }

    public void writeLayoutControl(Integer id, String layoutControl, Integer parentID) {
        _db.add(LAYOUT_CONTROL, id.toString(), layoutControl, parentID.toString());
    }

    public void writeSensitiveLayoutControl(Integer id, String layoutControl, Integer parentID) {
        _db.add(SENSITIVE_LAYOUT_CONTROL, id.toString(), layoutControl, parentID.toString());
    }

    void writeFieldInitialValue(IField f) {
        String fieldId = _rep.signature(f);
//        String valueString = f.getInitialValueString();
//        if (valueString != null && !valueString.equals("")) {
//            int pos = valueString.indexOf('@');
//            if (pos < 0)
//                System.err.println("Unexpected format (no @) in initial field value");
//            else {
//                try {
//                    int value = (int) Long.parseLong(valueString.substring(pos+1), 16); // parse hex string, possibly negative int
//                    _db.add(FIELD_INITIAL_VALUE, fieldId, Integer.toString(value));
//                } catch (NumberFormatException e) {
//                    _db.add(FIELD_INITIAL_VALUE, fieldId, valueString.substring(pos+1));
//                    // if we failed to parse the value as a hex int, output it in full
//                }
//            }
//        }
    }
}
