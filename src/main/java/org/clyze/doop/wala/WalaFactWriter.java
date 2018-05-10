package org.clyze.doop.wala;

import com.ibm.wala.analysis.typeInference.JavaPrimitiveType;
import com.ibm.wala.analysis.typeInference.TypeAbstraction;
import com.ibm.wala.analysis.typeInference.TypeInference;
import com.ibm.wala.classLoader.*;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.shrikeBT.ExceptionHandler;
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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.clyze.doop.common.PredicateFile.*;
import static org.clyze.doop.wala.WalaRepresentation.fixTypeString;

/**
 * FactWriter determines the format of a fact and adds it to a
 * database.
 */
public class WalaFactWriter {
    private Database _db;
    private WalaRepresentation _rep;
    private Map<String, String> _typeMap;/**
     * Used for logging various messages
     */
    protected Log logger;

    WalaFactWriter(Database db) {
        _db = db;
        _rep = WalaRepresentation.getRepresentation();
        _typeMap = new ConcurrentHashMap<>();
        logger =  LogFactory.getLog(getClass());

    }

    private String str(int i) {
        return String.valueOf(i);
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

        _db.add(STRING_RAW, result, result);
        _db.add(METHOD, result, _rep.simpleName(m), _rep.descriptor(m), writeType(m.getReference().getDeclaringClass()), writeType(m.getReturnType()), m.getDescriptor().toUnicodeString());
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
//        if (c.getTag("VisibilityAnnotationTag") != null) {
//            VisibilityAnnotationTag vTag = (VisibilityAnnotationTag) c.getTag("VisibilityAnnotationTag");
//            for (AnnotationTag aTag : vTag.getAnnotations()) {
//                _db.add(CLASS_ANNOTATION, classStr, soot.coffi.Util.v().jimpleTypeOfFieldDescriptor(aTag.getType()).getEscapedName());
//            }
//        }
    }

    void writeDirectSuperclass(IClass sub, IClass sup) {
        _db.add(DIRECT_SUPER_CLASS, writeType(sub), writeType(sup));
    }

    void writeDirectSuperinterface(IClass clazz, IClass iface) {
        _db.add(DIRECT_SUPER_IFACE, writeType(clazz), writeType(iface));
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

        // statement
        int index = session.calcInstructionNumber(instruction);
        String insn = _rep.instruction(m, instruction, session, index);
        String methodId = _rep.signature(m);
        _db.add(ASSIGN_HEAP_ALLOC, insn, str(index), heap, _rep.local(m, l), methodId, ""+getLineNumberFromInstruction(instruction));
    }

    private static int getLineNumberFromInstruction(SSAInstruction instruction) {
//        LineNumberTag tag = (LineNumberTag) stmt.getTag("LineNumberTag");
//        String lineNumber;
//        if (tag == null) {
//            return  0;
//        } else {
//            return tag.getLineNumber();
//        }
        return instruction.iindex;
        //return 0;
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
    void writeAssignNewMultiArrayExpr(IMethod m, SSANewInstruction instruction, Local l, Session session) {
        writeAssignNewMultiArrayExprHelper(m, instruction, l, _rep.local(m,l), instruction.getConcreteType(), session);
    }

    private void writeAssignNewMultiArrayExprHelper(IMethod m, SSANewInstruction instruction, Local l, String assignTo, TypeReference arrayType, Session session) {
        String heap = _rep.heapMultiArrayAlloc(m, instruction, arrayType, session);
        int index = session.calcInstructionNumber(instruction);
        String insn = _rep.instruction(m, instruction, session, index);


        String methodId = writeMethod(m);

        _db.add(NORMAL_HEAP, heap, writeType(arrayType));
        _db.add(ASSIGN_HEAP_ALLOC, insn, str(index), heap, assignTo, methodId, ""+getLineNumberFromInstruction(instruction));

        TypeReference componentType = getComponentType(arrayType);
        if (componentType.isArrayType()) {
            String childAssignTo = _rep.newLocalIntermediate(m, l, session);
            writeAssignNewMultiArrayExprHelper(m, instruction, l, childAssignTo, componentType, session);
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

    private void writeAssignStringConstant(IMethod m, SSAInstruction instruction, Local l, ConstantValue s, Session session) {
        int index = session.calcInstructionNumber(instruction);
        String constant = s.getValue().toString();
        String heapId = writeStringConstant(constant);

        String insn = _rep.instruction(m, instruction, session, index);
        String methodId = _rep.signature(m);

        _db.add(ASSIGN_HEAP_ALLOC, insn, str(index), heapId, _rep.local(m, l), methodId, ""+getLineNumberFromInstruction(instruction));
    }

    private void writeAssignNull(IMethod m, SSAInstruction instruction, Local l, Session session) {
        int index = session.calcInstructionNumber(instruction);
        String insn = _rep.instruction(m, instruction, session, index);
        String methodId = _rep.signature(m);

        _db.add(ASSIGN_NULL, insn, str(index), _rep.local(m, l), methodId);
    }

    private void writeAssignNumConstant(IMethod m, SSAInstruction instruction, Local l, ConstantValue constant, Session session) {
        int index = session.calcInstructionNumber(instruction);
        String insn = _rep.instruction(m, instruction, session, index);
        String methodId = _rep.signature(m);

        _db.add(ASSIGN_NUM_CONST, insn, str(index), constant.toString(), _rep.local(m, l), methodId);
    }
//
//    private void writeAssignMethodHandleConstant(IMethod m, Stmt stmt, Local l, MethodHandle constant, Session session) {
//        int index = session.calcInstructionNumber(stmt);
//        String insn = _rep.instruction(m, stmt, session, index);
//        String handleName = constant.getMethodRef().toString();
//        String heap = _rep.methodHandleConstant(handleName);
//        String methodId = writeMethod(m);
//
//        _db.add(METHOD_HANDLE_CONSTANT, heap, handleName);
//        _db.add(ASSIGN_HEAP_ALLOC, insn, str(index), heap, _rep.local(m, l), methodId, "0");
//    }

    private void writeAssignClassConstant(IMethod m, SSAInstruction instruction, Local l, ConstantValue constant, Session session) {
        String s = constant.toString();
        String heap;
        String actualType;

        TypeReference t;
        if(constant.getValue() instanceof TypeReference)
            t = (TypeReference) constant.getValue();
        else
            t = TypeReference.find(ClassLoaderReference.Primordial, s);

        heap = _rep.classConstant(t);
        actualType = fixTypeString(t.toString());

        _db.add(CLASS_HEAP, heap, actualType);
        int index = session.calcInstructionNumber(instruction);
        String insn = _rep.instruction(m, instruction, session, index);
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
        _db.add(APP_CLASS, writeType(application));
    }

    TypeReference getCorrectFieldDeclaringClass(FieldReference f, IClassHierarchy cha)
    {
        IClass targetClass = cha.lookupClass(f.getDeclaringClass());
        TypeReference typeRef = f.getDeclaringClass();
        if(targetClass == null)
            System.out.println("Failed to find class: " + fixTypeString(f.getDeclaringClass().getName().toString()) + " in class hierarchy.");
        else
        {
            for(IField field: targetClass.getAllFields())
            {
//            if(targetClass.getName().toString().contains(""))
//                System.out.println("");
                if(field.getName().toString().equals(f.getName().toString()))
                {
                    typeRef = field.getDeclaringClass().getReference();
                    break;
                }
            }
        }
        return typeRef;
    }

    String writeField(IField f) {
        String fieldId = _rep.signature(f);
        _db.add(FIELD_SIGNATURE, fieldId, writeType(f.getReference().getDeclaringClass()), _rep.simpleName(f), writeType(f.getFieldTypeReference()));
//        if (f.getTag("VisibilityAnnotationTag") != null) {
//            VisibilityAnnotationTag vTag = (VisibilityAnnotationTag) f.getTag("VisibilityAnnotationTag");
//            for (AnnotationTag aTag : vTag.getAnnotations()) {
//                _db.add(FIELD_ANNOTATION, fieldId, soot.coffi.Util.v().jimpleTypeOfFieldDescriptor(aTag.getType()).getEscapedName());
//            }
//        }
        return fieldId;
    }

    private String writeField(FieldReference f) {
        String fieldId = _rep.signature(f, f.getDeclaringClass());
        _db.add(FIELD_SIGNATURE, fieldId, writeType(f.getDeclaringClass()), _rep.simpleName(f), writeType(f.getFieldType()));
//        if (f.getTag("VisibilityAnnotationTag") != null) {
//            VisibilityAnnotationTag vTag = (VisibilityAnnotationTag) f.getTag("VisibilityAnnotationTag");
//            for (AnnotationTag aTag : vTag.getAnnotations()) {
//                _db.add(FIELD_ANNOTATION, fieldId, soot.coffi.Util.v().jimpleTypeOfFieldDescriptor(aTag.getType()).getEscapedName());
//            }
//        }
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

        //if (!(m.getReturnType() instanceof VoidType)) {
        String  var = _rep.nativeReturnVar(m);
        _db.add(NATIVE_RETURN_VAR, var, methodId);
        _db.add(VAR_TYPE, var, writeType(m.getReturnType()));
        _db.add(VAR_DECLARING_METHOD, var, methodId);
        //}
    }

    void writeGoto(IMethod m, SSAGotoInstruction instruction, SSAInstruction to, Session session) {
        //session.calcInstructionNumber(instruction);
        int index = session.getInstructionNumber(instruction);
        //session.calcInstructionNumber(to);
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
        int index = session.getInstructionNumber(instruction);
        //session.calcInstructionNumber(to);
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

    private int getNextNonNullInstruction(IR ir, int instructionIndex)
    {
        SSAInstruction[] ssaInstructions = ir.getInstructions();
        //ISSABasicBlock basicBlock = ir.getBasicBlockForInstruction(ssaInstructions[instructionIndex]);
        for(int i = instructionIndex +1 ; i < ssaInstructions.length; i++)
        {
            if(ssaInstructions[i]!=null)
                return i;
        }
        return -1;
    }

    void writeLookupSwitch(IR ir,IMethod inMethod, SSASwitchInstruction instruction, Session session, Local switchVar) {
        int instrIndex = session.getInstructionNumber(instruction);
        int targetIndex, targetWALAIndex;
        int defaultIndex, defaultWALAIndex;
        //Value v = writeImmediate(inMethod, instruction, instruction.getUse(0), session);
        String insn = _rep.instruction(inMethod, instruction, session, instrIndex);
        String methodId = _rep.signature(inMethod);

        _db.add(LOOKUP_SWITCH, insn, str(instrIndex), _rep.local(inMethod, switchVar), methodId);

        int casesAndLabels[] = instruction.getCasesAndLabels();
        SSAInstruction instructions[] = ir.getInstructions();
        for(int i = 0; i < casesAndLabels.length; i+=2) {
            int tgIndex = casesAndLabels[i];
            //session.calcInstructionNumber(instructions[casesAndLabels[i+1]]);
            if(instructions[casesAndLabels[i+1]]==null)
            {
                targetWALAIndex = getNextNonNullInstruction(ir,casesAndLabels[i+1]);
                if(targetWALAIndex == -1)
                    logger.error("Error: Next non-null instruction index = -1");
                targetIndex = session.getInstructionNumber(instructions[targetWALAIndex]);
            }
            else
                targetIndex = session.getInstructionNumber(instructions[casesAndLabels[i+1]]);

            _db.add(LOOKUP_SWITCH_TARGET, insn, str(tgIndex), str(targetIndex));
        }

        if(instructions[instruction.getDefault()] == null)
        {
            defaultWALAIndex = getNextNonNullInstruction(ir,instruction.getDefault());
            if(defaultWALAIndex == -1)
                logger.error("Error: Next non-null instruction index = -1");
            defaultIndex = session.getInstructionNumber(instructions[defaultWALAIndex]);
        }
        else {
            //session.calcInstructionNumber(instructions[instruction.getDefault()]);
            defaultIndex = session.getInstructionNumber(instructions[instruction.getDefault()]);
        }

        _db.add(LOOKUP_SWITCH_DEFAULT, insn, str(defaultIndex));
    }

    void writeUnsupported(IMethod m, SSAInstruction instruction, Session session) {
        int index = session.calcInstructionNumber(instruction);
        String insn = _rep.unsupported(m, instruction, index);
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
            prevType =prevTypes.next();

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
        SSACFG cfg = ir.getControlFlowGraph();
        session.calcInstructionNumber(catchInstr);
        int handlerIndex = session.getInstructionNumber(catchInstr);
        //System.out.println("catch def is " + catchInstr.getDef());
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
            session.calcInstructionNumber(startInstr);
            int beginIndex = session.getInstructionNumber(startInstr);
            session.calcInstructionNumber(endInstr);
            int endIndex = session.getInstructionNumber(endInstr);
            Iterator<TypeReference> excTypes = handlerBlock.getCaughtExceptionTypes();
            if(m.getName().toString().equals("parseNetscapeCertChain") &&
                    m.getDeclaringClass().getName().toString().contains("PKCS7"))
                System.out.println("WALA " + handlerBlock.getFirstInstructionIndex() +" ("+
                        scopeArray[i] + " - " + scopeArray[i + 1] + ") DOOP "+
                        handlerIndex +" ("+ beginIndex + " - " + endIndex + ")");
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

    void writeExceptionHandlerOld(IR ir, IMethod m, SSACFG.ExceptionHandlerBasicBlock handlerBlock, Session session, TypeInference typeInference) {

        SSAGetCaughtExceptionInstruction catchInstr = handlerBlock.getCatchInstruction();
        if(catchInstr == null)
        {
            //System.out.println("NULL CATCH?");
            return;
        }
        SSACFG cfg = ir.getControlFlowGraph();
        Collection<ISSABasicBlock> excPredecessors = cfg.getExceptionalPredecessors(handlerBlock);
        int index = session.calcInstructionNumber(catchInstr);
        //System.out.println("catch def is " + catchInstr.getDef());
        Local caught = createLocal(ir, catchInstr, catchInstr.getDef(),typeInference);

        int handlerIndex = session.getInstructionNumber(catchInstr);
        SSAInstruction[] instructions = ir.getInstructions();
        SSAInstruction startInstr = null;
        SSAInstruction endInstr = null;
        int startInstrIndex = -5;
        int endInstrIndex = -5;
        // Because in SSACFG.getExceptionalPredecessors():
        // "The order of blocks returned should be arbitrary but deterministic."
        // We do not know their order, so we need to iterate through them to find which is
        // first and which is last.
        for(ISSABasicBlock bb : excPredecessors)
        {
            if(startInstrIndex == -5)
            {
                startInstrIndex = bb.getFirstInstructionIndex();
                endInstrIndex = bb.getLastInstructionIndex();
            }
            else
            {
                if(bb.getFirstInstructionIndex() < startInstrIndex)
                    startInstrIndex = bb.getFirstInstructionIndex();
                if(bb.getLastInstructionIndex() > endInstrIndex)
                    endInstrIndex = bb.getLastInstructionIndex();
            }
        }

        IBytecodeMethod shrikeMethod = (IBytecodeMethod) m;
        ExceptionHandler[][] exceptionHandlers = null;
        try {
            exceptionHandlers = shrikeMethod.getHandlers();
        } catch (InvalidClassFileException e) {
            e.printStackTrace();
        }


        // We need to use startInstrIndex and endInstrIndex as a scope
        // and find the first and last instruction between them because
        // either instructions[startInstrIndex] or instructions[endInstrIndex]
        // could be null
        for(int i=startInstrIndex; i <= endInstrIndex; i++)
        {
            if(instructions[i] != null)
            {
                if(startInstr == null)
                {
                    startInstr = instructions[i];
                    endInstr = instructions[i];
                }
                else
                    endInstr = instructions[i];
            }
        }
        if(startInstr == null) {//Basic block has no instructions
            //System.out.println("NO instructions in handler block :(.");
            return;
        }

        if(exceptionHandlers != null)
        {
//            if(m.getName().toString().equals("parseNetscapeCertChain"))
//                for(int i=0; i < handlerIndex; i++)
//                    if(instructions[i] != null) {
//                        System.out.println(session.getInstructionNumber(instructions[i]) + " " + instructions[i].toString(ir.getSymbolTable()));
//                        for(int j :handlersToArray(exceptionHandlers[i]))
//                            System.out.print(j +", ");
//                        System.out.print("\n");
//                    }
            startInstrIndex =startInstr.iindex;
            int[] upperBoundHandlerArray = handlersToArray(exceptionHandlers[startInstrIndex]);
            while( startInstrIndex > 0 && java.util.Arrays.equals(upperBoundHandlerArray, handlersToArray(exceptionHandlers[startInstrIndex - 1])))
                startInstrIndex--;
            endInstrIndex =endInstr.iindex;
            int[] lowerBoundHandlerArray = handlersToArray(exceptionHandlers[endInstrIndex]);
            while( endInstrIndex < instructions.length -1 && java.util.Arrays.equals(lowerBoundHandlerArray, handlersToArray(exceptionHandlers[endInstrIndex + 1])))
                endInstrIndex++;

            startInstr = null;
            endInstr = null;
            for(int i=startInstrIndex; i <= endInstrIndex; i++)
            {
                if(instructions[i] != null)
                {
                    if(startInstr == null)
                    {
                        startInstr = instructions[i];
                        endInstr = instructions[i];
                    }
                    else
                        endInstr = instructions[i];
                }
            }

        }

        session.calcInstructionNumber(startInstr);
        int beginIndex = session.getInstructionNumber(startInstr);
        session.calcInstructionNumber(endInstr);
        int endIndex = session.getInstructionNumber(endInstr);
        Iterator<TypeReference> excTypes = handlerBlock.getCaughtExceptionTypes();
        String prev = null;
        while(excTypes.hasNext())
        {
            TypeReference excType = excTypes.next();
            String insn = _rep.handler(m, catchInstr, excType, session,0);
            _db.add(EXCEPTION_HANDLER, insn, _rep.signature(m), str(handlerIndex), fixTypeString(excType.getName().toString()), _rep.local(m, caught), str(beginIndex), str(endIndex + 1));
            if(prev != null)
                _db.add(EXCEPT_HANDLER_PREV, insn, prev);
            prev = insn;
        }
    }

    int[] handlersToArray(ExceptionHandler[] handlers)
    {
        int[] targetsArray = new int[handlers.length];
        for(int i=0; i < handlers.length ; i++)
        {
            targetsArray[i] = handlers[i].getHandler();
        }
        return targetsArray;
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

    void writeStringConstantExpression(IMethod inMethod, SSAInstruction instruction, Local l, ConstantValue constant, Session session) {
        // introduce a new temporary variable
        writeLocal(inMethod, l);
        writeAssignStringConstant(inMethod, instruction, l, constant, session);
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

//    private Local writeMethodHandleConstantExpression(IMethod inMethod, SSAInstruction instruction, ConstantValue constant, Session session) {
//        // introduce a new temporary variable
//        String basename = "$mhandleconstant";
//        String varname = basename + session.nextNumber(basename);
//        Local l = new Local(varname, TypeReference.JavaLangInvokeMethodHandle);
//        writeLocal(inMethod, l);
//        //writeAssignMethodHandleConstant(inMethod, instruction, l, constant, session);
//        return l;
//    }



    private void writeActualParams(IMethod inMethod, IR ir, SSAInvokeInstruction instruction, String invokeExprRepr, Session session, TypeInference typeInference) {
        if (instruction.isStatic()) {
            for (int i = 0; i < instruction.getNumberOfParameters(); i++) {
                Local l = createLocal(ir, instruction, instruction.getUse(i), typeInference);
                _db.add(ACTUAL_PARAMETER, str(i), invokeExprRepr, _rep.local(inMethod, l));
            }
        }
        else {
            for (int i = 1; i < instruction.getNumberOfParameters(); i++) {
                Local l = createLocal(ir, instruction, instruction.getUse(i), typeInference);
                _db.add(ACTUAL_PARAMETER, str(i-1), invokeExprRepr, _rep.local(inMethod, l));
            }

        }
        if (instruction instanceof SSAInvokeDynamicInstruction) {
            for (int j = 0; j < ((SSAInvokeDynamicInstruction) instruction).getBootstrap().callArgumentCount(); j++) {
                int arg = ((SSAInvokeDynamicInstruction) instruction).getBootstrap().callArgumentIndex(j);

                Local l = createLocal(ir, instruction, arg); //TODO: TypeInference for bootstrap parameters??
                _db.add(BOOTSTRAP_PARAMETER, str(j), invokeExprRepr, _rep.local(inMethod, l));

            }
        }
    }

    void writeInvoke(IMethod inMethod, IR ir, SSAInvokeInstruction instruction, Local to, Session session, TypeInference typeInference) {
        String insn = writeInvokeHelper(inMethod, ir, instruction, session, typeInference);
        if(to != null)
            _db.add(ASSIGN_RETURN_VALUE, insn, _rep.local(inMethod, to));
    }

    private String writeInvokeHelper(IMethod inMethod, IR ir, SSAInvokeInstruction instruction, Session session, TypeInference typeInference) {
        session.calcInstructionNumber(instruction);
        int index = session.getInstructionNumber(instruction);
        //String insn = _rep.invoke(inMethod, instruction, session);
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

        //writeActualParams(inMethod, ir, instruction, insn, session,typeInference);
        IClassHierarchy cha = inMethod.getClassHierarchy();
        MethodReference targetRef = instruction.getCallSite().getDeclaredTarget();
        IClass targetClass = cha.lookupClass(targetRef.getDeclaringClass());
//        if(instruction.isDispatch()) {
//            int dispVar = instruction.getUse(0);
//            TypeReference typeRef;
//            TypeAbstraction typeAbstraction = typeInference.getType(dispVar);
//            IClass dispClass;
//            if(!(typeAbstraction.getType() == null && !(typeAbstraction instanceof JavaPrimitiveType)))
//                typeRef = TypeReference.JavaLangObject;
//            else
//                typeRef = typeAbstraction.getTypeReference();
//            dispClass = cha.lookupClass(typeRef);
//            if(!targetClass.toString().equals(dispClass.toString()))
//                System.out.println("DIFF " + targetClass.toString() + " | " + dispClass.toString());
//
//        }
        if(targetClass == null)
            System.out.println("Failed to find class: "  + fixTypeString(targetRef.getDeclaringClass().getName().toString()) + " in class chierarchy.");
        else if( targetClass.isArrayClass())
        {
            //System.out.println("ArrayClass " +targetClass.getName().toString() + " is a child of " + supClass.getName().toString());
            for(IMethod meth: targetClass.getAllMethods()) {
                if (meth.getName().toString().equals(targetRef.getName().toString())
                        && meth.getDescriptor().toString().equals(targetRef.getDescriptor().toString()))
                {
                    //System.out.println("\n Target found: " + meth.toString());
                    targetRef = meth.getReference();
                    break;
                }
            }
        }//else if(targetClass.isInterface())
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
            if(targetClass.isAbstract() && ! targetClass.isInterface() && !foundAtFirst)
            {
                Queue<IClass> classQueue = new LinkedList<>();
                classQueue.addAll(targetClass.getDirectInterfaces());
                if(!classQueue.isEmpty())
                {
                    boolean found = false;
                    IClass currClass;
//                    for(IClass c:targetClass.getDirectInterfaces()) {
//                        //classQueue.add(c);
//                        System.out.println("Class " + targetClass.getReference().toString() +" implements " + c.getReference().toString());
//                    }
                    while (!classQueue.isEmpty() && !found) {
                        currClass = classQueue.remove();
                        //System.out.println("------------------------------------------------");
                        //System.out.println("Looking in interface " + currClass.getReference().toString());
                        for(IMethod meth: currClass.getDeclaredMethods()) {
                            //System.out.println(meth.getReference().toString());
                            if (meth.getName().toString().equals(targetRef.getName().toString())
                                    && meth.getDescriptor().toString().equals(targetRef.getDescriptor().toString()))
                            {
                                //System.out.println("\n Target found in interface: " + meth.toString());
                                targetRef = meth.getReference();
                                found = true;
                                break;
                            }
                        }//System.out.println("------------------------------------------------");
                        classQueue.addAll(currClass.getDirectInterfaces());
                    }
                }
            }
        }

        String insn = _rep.invoke(inMethod, instruction, targetRef, session);
        writeActualParams(inMethod, ir, instruction, insn, session,typeInference);

        if(sourceLineNum != -1)
            _db.add(METHOD_INV_LINE, insn, str(sourceLineNum));

        if (instruction.isStatic()) {
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
        else if (instruction instanceof SSAInvokeDynamicInstruction) {
            MethodReference dynInfo = instruction.getDeclaredTarget();
            String dynArity = String.valueOf(dynInfo.getNumberOfParameters());

            StringBuilder parameterTypes = new StringBuilder();
            for (int i = 0; i < dynInfo.getNumberOfParameters(); i++) {
                if (i==0) {
                    parameterTypes.append(dynInfo.getParameterType(i));
                }
                else {
                    parameterTypes.append(", ").append(dynInfo.getParameterType(i));
                }
            }
            _db.add(DYNAMIC_METHOD_INV, insn, str(index), dynInfo.getSignature(), dynInfo.getName().toString(), dynInfo.getReturnType().toString(), dynArity, parameterTypes.toString(), methodId);
        }
        else {
            throw new RuntimeException("Cannot handle invoke instruction: " + instruction);
        }

        return insn;
    }

    private Local createLocal(IR ir, SSAInstruction instruction, int varIndex) {
        Local l;
        String[] localNames = ir.getLocalNames(instruction.iindex, varIndex);

        if (localNames != null) {
            assert localNames.length == 1;
            l = new Local("v" + varIndex, varIndex, localNames[0],TypeReference.JavaLangObject);
        }
        else {
            l = new Local("v" + varIndex, varIndex, TypeReference.JavaLangObject);
        }
        return l;
    }

    private Local createLocal(IR ir, SSAInstruction instruction, int varIndex, TypeInference typeInference) {
        Local l;
        String[] localNames ;
        if(instruction.iindex == -1)//Instructions not on the normal instructions array of the IR can have iindex==-1 ex SSAGetCaughtExceptionInstruction, SSAPhiInstruction
            localNames = null;
        else
            localNames = ir.getLocalNames(instruction.iindex, varIndex);

        TypeReference typeRef;
        TypeAbstraction typeAbstraction = typeInference.getType(varIndex);
        if(typeAbstraction.getType() == null && !(typeAbstraction instanceof JavaPrimitiveType))
            typeRef = TypeReference.JavaLangObject;
        else
            typeRef = typeAbstraction.getTypeReference();

        if (localNames != null) {
            l = new Local("v" + varIndex, varIndex, localNames[0], typeRef);
        }
        else {
            l = new Local("v" + varIndex, varIndex, typeRef);
        }
        if(ir.getSymbolTable().isConstant(varIndex) && ! ir.getSymbolTable().isNullConstant(varIndex))
            l.setValue(ir.getSymbolTable().getConstantValue(varIndex).toString());
        return l;
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
        _db.add(ASSIGN_OPER_TYPE, insn, instruction.getOperator().toString());

        _db.add(ASSIGN_OPER_FROM, insn, _rep.local(m, op1));
        _db.add(ASSIGN_OPER_FROM, insn, _rep.local(m, op2));

    }

    void writeAssignBinop(IMethod m, SSABinaryOpInstruction instruction, Local left, Local op1, Local op2, Session session) {
        int index = session.calcInstructionNumber(instruction);
        String insn = _rep.instruction(m, instruction, session, index);
        String methodId = _rep.signature(m);

        _db.add(ASSIGN_BINOP, insn, str(index), _rep.local(m, left), methodId);
        _db.add(ASSIGN_OPER_TYPE, insn, instruction.getOperator().toString());

        _db.add(ASSIGN_OPER_FROM, insn, _rep.local(m, op1));
        _db.add(ASSIGN_OPER_FROM, insn, _rep.local(m, op2));

    }
    //
    void writeAssignUnop(IMethod m, SSAUnaryOpInstruction instruction, Local to, Local from, Session session) {
        int index = session.calcInstructionNumber(instruction);
        String insn = _rep.instruction(m, instruction, session, index);
        String methodId = _rep.signature(m);

        _db.add(ASSIGN_UNOP, insn, str(index), _rep.local(m, to), methodId);

        if (instruction.getOpcode().toString().equals("neg") )
            _db.add(ASSIGN_OPER_TYPE, insn, " !");

        _db.add(ASSIGN_OPER_FROM, insn, _rep.local(m, from));
    }

    void writeAssignArrayLength(IMethod m, SSAArrayLengthInstruction instruction, Local to, Local from, Session session) {
        int index = session.calcInstructionNumber(instruction);
        String insn = _rep.instruction(m, instruction, session, index);
        String methodId = _rep.signature(m);

        _db.add(ASSIGN_UNOP, insn, str(index), _rep.local(m, to), methodId);
        _db.add(ASSIGN_OPER_TYPE, insn, " length ");
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
//    void writePhantomInvoke(IMethod m, Stmt stmt, Session session) {
////        int index = session.calcInstructionNumber(stmt);
////        String insn = _rep.instruction(m, stmt, session, index);
////        String methodId = writeMethod(m);
////
////        _db.add(PHANTOM_INVOKE, insn, str(index), methodId);
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
