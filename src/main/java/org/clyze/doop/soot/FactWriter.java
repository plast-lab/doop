package org.clyze.doop.soot;

import org.clyze.doop.common.Database;
import org.clyze.doop.common.FactEncoders;
import org.clyze.doop.common.PredicateFile;
import soot.*;
import soot.jimple.*;
import soot.jimple.internal.JimpleLocal;
import soot.jimple.toolkits.typing.fast.BottomType;
import soot.tagkit.AnnotationTag;
import soot.tagkit.LineNumberTag;
import soot.tagkit.VisibilityAnnotationTag;
import soot.tagkit.VisibilityParameterAnnotationTag;
import soot.util.backend.ASMBackendUtils;

import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

import static org.clyze.doop.common.PredicateFile.*;

/**
 * FactWriter determines the format of a fact and adds it to a
 * database.
 */
public class FactWriter {
    private Database _db;
    private Representation _rep;
    private Map<String, Type> _varTypeMap;

    FactWriter(Database db) {
        _db = db;
        _rep = Representation.getRepresentation();
        _varTypeMap = new ConcurrentHashMap<>();
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

    String writeMethod(SootMethod m) {
        String methodRaw = _rep.signature(m);
        String result = hashMethodNameIfLong(methodRaw);

        _db.add(STRING_RAW, result, methodRaw);
        _db.add(METHOD, result, _rep.simpleName(m), _rep.descriptor(m), writeType(m.getDeclaringClass()), writeType(m.getReturnType()), ASMBackendUtils.toTypeDesc(m.makeRef()));
        if (m.getTag("VisibilityAnnotationTag") != null) {
            VisibilityAnnotationTag vTag = (VisibilityAnnotationTag) m.getTag("VisibilityAnnotationTag");
            for (AnnotationTag aTag : vTag.getAnnotations()) {
                _db.add(METHOD_ANNOTATION, result, soot.coffi.Util.v().jimpleTypeOfFieldDescriptor(aTag.getType()).getEscapedName());
            }
        }
        if (m.getTag("VisibilityParameterAnnotationTag") != null) {
            VisibilityParameterAnnotationTag vTag = (VisibilityParameterAnnotationTag) m.getTag("VisibilityParameterAnnotationTag");

            ArrayList<VisibilityAnnotationTag> annList = vTag.getVisibilityAnnotations();
            for (int i = 0; i < annList.size(); i++) {
                if (annList.get(i) != null) {
                    for (AnnotationTag aTag : annList.get(i).getAnnotations()) {
                        _db.add(PARAM_ANNOTATION, result, str(i), soot.coffi.Util.v().jimpleTypeOfFieldDescriptor(aTag.getType()).getEscapedName());
                    }
                }
            }
        }
        return result;
    }

    void writeClassArtifact(String artifact, String className) { _db.add(CLASS_ARTIFACT, artifact, className); }

    void writeAndroidEntryPoint(SootMethod m) {
        _db.add(ANDROID_ENTRY_POINT, _rep.signature(m));
    }

    void writeAndroidKeepMethod(String methodSig) {
        _db.add(ANDROID_KEEP_METHOD, "<" + methodSig + ">");
    }

    void writeAndroidKeepClass(String className) {
        _db.add(ANDROID_KEEP_CLASS, className);
    }

    void writeProperty(String path, String key, String value) {
        String pathId = writeStringConstant(path);
        String keyId = writeStringConstant(key);
        String valueId = writeStringConstant(value);
        _db.add(PROPERTIES, pathId, keyId, valueId);
    }

    void writeClassOrInterfaceType(SootClass c) {
        String classStr = c.getName();
        if (c.isInterface()) {
            _db.add(INTERFACE_TYPE, classStr);
        }
        else {
            _db.add(CLASS_TYPE, classStr);
        }
        _db.add(CLASS_HEAP, _rep.classConstant(c), classStr);
        if (c.getTag("VisibilityAnnotationTag") != null) {
            VisibilityAnnotationTag vTag = (VisibilityAnnotationTag) c.getTag("VisibilityAnnotationTag");
            for (AnnotationTag aTag : vTag.getAnnotations()) {
                _db.add(CLASS_ANNOTATION, classStr, soot.coffi.Util.v().jimpleTypeOfFieldDescriptor(aTag.getType()).getEscapedName());
            }
        }
    }

    void writeDirectSuperclass(SootClass sub, SootClass sup) {
        _db.add(DIRECT_SUPER_CLASS, writeType(sub), writeType(sup));
    }

    void writeDirectSuperinterface(SootClass clazz, SootClass iface) {
        _db.add(DIRECT_SUPER_IFACE, writeType(clazz), writeType(iface));
    }

    private String writeType(SootClass c) {
        String classStr = c.getName();
        // The type itself is already taken care of by writing the
        // SootClass declaration, so we don't actually write the type
        // here, and just return the string.
        return classStr;
    }

    private String writeType(Type t) {
        String result = t.toString();

        if (t instanceof ArrayType) {
            _db.add(ARRAY_TYPE, result);
            Type componentType = ((ArrayType) t).getElementType();
            _db.add(COMPONENT_TYPE, result, writeType(componentType));
        }
        else if (t instanceof PrimType || t instanceof NullType ||
                 t instanceof RefType || t instanceof VoidType || t instanceof BottomType) {
            // taken care of by the standard facts
            ;
        }
        else {
            throw new RuntimeException("Don't know what to do with type " + t);
        }

        return result;
    }

    void writePhantomType(Type t) {
        _db.add(PHANTOM_TYPE, writeType(t));
    }

    void writePhantomMethod(SootMethod m) {
        String sig = writeMethod(m);
        System.out.println("Method " + sig + " is phantom.");
        _db.add(PHANTOM_METHOD, sig);
    }

    void writePhantomBasedMethod(SootMethod m) {
        String sig = writeMethod(m);
        System.out.println("Method signature " + sig + " contains phantom types.");
        _db.add(PHANTOM_BASED_METHOD, sig);
    }

    void writeEnterMonitor(SootMethod m, EnterMonitorStmt stmt, Local var, Session session) {
        int index = session.calcUnitNumber(stmt);
        String insn = _rep.instruction(m, stmt, session, index);
        String methodId = writeMethod(m);

        _db.add(ENTER_MONITOR, insn, str(index), _rep.local(m, var), methodId);
    }

    void writeExitMonitor(SootMethod m, ExitMonitorStmt stmt, Local var, Session session) {
        int index = session.calcUnitNumber(stmt);
        String insn = _rep.instruction(m, stmt, session, index);
        String methodId = writeMethod(m);

        _db.add(EXIT_MONITOR, insn, str(index), _rep.local(m, var), methodId);
    }

    void writeAssignLocal(SootMethod m, Stmt stmt, Local to, Local from, Session session) {
        int index = session.calcUnitNumber(stmt);
        String insn = _rep.instruction(m, stmt, session, index);
        String methodId = writeMethod(m);

        _db.add(ASSIGN_LOCAL, insn, str(index), _rep.local(m, from), _rep.local(m, to), methodId);
    }

    void writeAssignLocal(SootMethod m, Stmt stmt, Local to, ThisRef ref, Session session) {
        int index = session.calcUnitNumber(stmt);
        String insn = _rep.instruction(m, stmt, session, index);
        String methodId = writeMethod(m);

        _db.add(ASSIGN_LOCAL, insn, str(index), _rep.thisVar(m), _rep.local(m, to), methodId);
    }

    void writeAssignLocal(SootMethod m, Stmt stmt, Local to, ParameterRef ref, Session session) {
        int index = session.calcUnitNumber(stmt);
        String insn = _rep.instruction(m, stmt, session, index);
        String methodId = writeMethod(m);

        _db.add(ASSIGN_LOCAL, insn, str(index), _rep.param(m, ref.getIndex()), _rep.local(m, to), methodId);
    }

    void writeAssignInvoke(SootMethod inMethod, Stmt stmt, Local to, InvokeExpr expr, Session session) {
        String insn = writeInvokeHelper(inMethod, stmt, expr, session);

        _db.add(ASSIGN_RETURN_VALUE, insn, _rep.local(inMethod, to));
    }

    void writeAssignHeapAllocation(SootMethod m, Stmt stmt, Local l, AnyNewExpr expr, Session session) {
        String heap = _rep.heapAlloc(m, expr, session);


        _db.add(NORMAL_HEAP, heap, writeType(expr.getType()));

        if (expr instanceof NewArrayExpr) {
            NewArrayExpr newArray = (NewArrayExpr) expr;
            Value sizeVal = newArray.getSize();

            if (sizeVal instanceof IntConstant) {
                IntConstant size = (IntConstant) sizeVal;

                if(size.value == 0)
                    _db.add(EMPTY_ARRAY, heap);
            }
        }

        // statement
        int index = session.calcUnitNumber(stmt);
        String insn = _rep.instruction(m, stmt, session, index);
        String methodId = writeMethod(m);
        _db.add(ASSIGN_HEAP_ALLOC, insn, str(index), heap, _rep.local(m, l), methodId, ""+getLineNumberFromStmt(stmt));
    }

    private static int getLineNumberFromStmt(Stmt stmt) {
        LineNumberTag tag = (LineNumberTag) stmt.getTag("LineNumberTag");
        String lineNumber;
        if (tag == null) {
            return  0;
        } else {
            return tag.getLineNumber();
        }
    }

    private Type getComponentType(ArrayType type) {
        // Soot calls the component type of an array type the "element
        // type", which is rather confusing, since in an array type
        // A[][][], the JVM Spec defines A to be the element type, and
        // A[][] is the component type.
        return type.getElementType();
    }

    /**
     * NewMultiArray is slightly complicated because an array needs to
     * be allocated separately for every dimension of the array.
     */
    void writeAssignNewMultiArrayExpr(SootMethod m, Stmt stmt, Local l, NewMultiArrayExpr expr, Session session) {
        writeAssignNewMultiArrayExprHelper(m, stmt, l, _rep.local(m,l), expr, (ArrayType) expr.getType(), session);
    }

    private void writeAssignNewMultiArrayExprHelper(SootMethod m, Stmt stmt, Local l, String assignTo, NewMultiArrayExpr expr, ArrayType arrayType, Session session) {
        String heap = _rep.heapMultiArrayAlloc(m, expr, arrayType, session);
        int index = session.calcUnitNumber(stmt);
        String insn = _rep.instruction(m, stmt, session, index);


        String methodId = writeMethod(m);

        _db.add(NORMAL_HEAP, heap, writeType(arrayType));
        _db.add(ASSIGN_HEAP_ALLOC, insn, str(index), heap, assignTo, methodId, ""+getLineNumberFromStmt(stmt));

        Type componentType = getComponentType(arrayType);
        if (componentType instanceof ArrayType) {
            String childAssignTo = _rep.newLocalIntermediate(m, l, session);
            writeAssignNewMultiArrayExprHelper(m, stmt, l, childAssignTo, expr, (ArrayType) componentType, session);
            int storeInsnIndex = session.calcUnitNumber(stmt);
            String storeInsn = _rep.instruction(m, stmt, session, storeInsnIndex);

            _db.add(STORE_ARRAY_INDEX, storeInsn, str(storeInsnIndex), childAssignTo, assignTo, methodId);
            _db.add(VAR_TYPE, childAssignTo, writeType(componentType));
            _db.add(VAR_DECLARING_METHOD, childAssignTo, methodId);
        }
    }

    // The commented-out code below is what used to be in Doop2. It is not
    // equivalent to code in old Doop. I (YS) tried to have a more compatible
    // approach for comparison purposes.
    /*
    public void writeAssignNewMultiArrayExpr(SootMethod m, Stmt stmt, Local l, NewMultiArrayExpr expr, Session session) {
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

    void writeAssignStringConstant(SootMethod m, Stmt stmt, Local l, StringConstant s, Session session) {
        String constant = s.toString();
        String content = constant.substring(1, constant.length() - 1);
        String heapId = writeStringConstant(content);

        int index = session.calcUnitNumber(stmt);
        String insn = _rep.instruction(m, stmt, session, index);
        String methodId = writeMethod(m);

        _db.add(ASSIGN_HEAP_ALLOC, insn, str(index), heapId, _rep.local(m, l), methodId, ""+getLineNumberFromStmt(stmt));
    }

    void writeAssignNull(SootMethod m, Stmt stmt, Local l, Session session) {
        int index = session.calcUnitNumber(stmt);
        String insn = _rep.instruction(m, stmt, session, index);
        String methodId = writeMethod(m);

        _db.add(ASSIGN_NULL, insn, str(index), _rep.local(m, l), methodId);
    }

    void writeAssignNumConstant(SootMethod m, Stmt stmt, Local l, NumericConstant constant, Session session) {
        int index = session.calcUnitNumber(stmt);
        String insn = _rep.instruction(m, stmt, session, index);
        String methodId = writeMethod(m);

        _db.add(ASSIGN_NUM_CONST, insn, str(index), constant.toString(), _rep.local(m, l), methodId);
    }

    private void writeAssignMethodHandleConstant(SootMethod m, Stmt stmt, Local l, MethodHandle constant, Session session) {
        int index = session.calcUnitNumber(stmt);
        String insn = _rep.instruction(m, stmt, session, index);
        String handleName = constant.getMethodRef().toString();
        String heap = _rep.methodHandleConstant(handleName);
        String methodId = writeMethod(m);

        _db.add(METHOD_HANDLE_CONSTANT, heap, handleName);
        _db.add(ASSIGN_HEAP_ALLOC, insn, str(index), heap, _rep.local(m, l), methodId, "0");
    }

    void writeAssignClassConstant(SootMethod m, Stmt stmt, Local l, ClassConstant constant, Session session) {
        String s = constant.getValue().replace('/', '.');
        String heap;
        String actualType;

        /* There is some weirdness in class constants: normal Java class
           types seem to have been translated to a syntax with the initial
           L, but arrays are still represented as [, for example [C for
           char[] */
        if (s.charAt(0) == '[' || (s.charAt(0) == 'L' && s.endsWith(";")) ) {
            // array type
            Type t = soot.coffi.Util.v().jimpleTypeOfFieldDescriptor(s);

            heap = _rep.classConstant(t);
            actualType = t.toString();
        }
        else {
//            SootClass c = soot.Scene.v().getSootClass(s);
//            if (c == null) {
//                throw new RuntimeException("Unexpected class constant: " + constant);
//            }
//
//            heap =  _rep.classConstant(c);
//            actualType = c.getName();
////              if (!actualType.equals(s))
////                  System.out.println("hallelujah!\n\n\n\n");
            // The code above should be functionally equivalent with the simple code below,
            // but the above causes a concurrent modification exception due to a Soot
            // bug that adds a phantom class to the Scene's hierarchy, although
            // (based on their own comments) it shouldn't.
            heap = _rep.classConstant(s);
            actualType = s;
        }

        _db.add(CLASS_HEAP, heap, actualType);

        int index = session.calcUnitNumber(stmt);
        String insn = _rep.instruction(m, stmt, session, index);
        String methodId = writeMethod(m);

        // REVIEW: the class object is not explicitly written. Is this always ok?
        _db.add(ASSIGN_HEAP_ALLOC, insn, str(index), heap, _rep.local(m, l), methodId, "0");
    }

    void writeAssignCast(SootMethod m, Stmt stmt, Local to, Local from, Type t, Session session) {
        int index = session.calcUnitNumber(stmt);
        String insn = _rep.instruction(m, stmt, session, index);
        String methodId = writeMethod(m);

        _db.add(ASSIGN_CAST, insn, str(index), _rep.local(m, from), _rep.local(m, to), writeType(t), methodId);
    }

    void writeAssignCastNumericConstant(SootMethod m, Stmt stmt, Local to, NumericConstant constant, Type t, Session session) {
        int index = session.calcUnitNumber(stmt);
        String insn = _rep.instruction(m, stmt, session, index);
        String methodId = writeMethod(m);

        _db.add(ASSIGN_CAST_NUM_CONST, insn, str(index), constant.toString(), _rep.local(m, to), writeType(t), methodId);
    }

    void writeAssignCastNull(SootMethod m, Stmt stmt, Local to, Type t, Session session) {
        int index = session.calcUnitNumber(stmt);
        String insn = _rep.instruction(m, stmt, session, index);
        String methodId = writeMethod(m);

        _db.add(ASSIGN_CAST_NULL, insn, str(index), _rep.local(m, to), writeType(t), methodId);
    }

    void writeStoreInstanceField(SootMethod m, Stmt stmt, SootField f, Local base, Local from, Session session) {
        writeInstanceField(m, stmt, f, base, from, session, STORE_INST_FIELD);
    }

    void writeLoadInstanceField(SootMethod m, Stmt stmt, SootField f, Local base, Local to, Session session) {
        writeInstanceField(m, stmt, f, base, to, session, LOAD_INST_FIELD);
    }

    private void writeInstanceField(SootMethod m, Stmt stmt, SootField f, Local base, Local var, Session session, PredicateFile storeInstField) {
        int index = session.calcUnitNumber(stmt);
        String insn = _rep.instruction(m, stmt, session, index);
        String methodId = writeMethod(m);

        String fieldId = writeField(f);
        _db.add(storeInstField, insn, str(index), _rep.local(m, var), _rep.local(m, base), fieldId, methodId);
    }

    void writeStoreStaticField(SootMethod m, Stmt stmt, SootField f, Local from, Session session) {
        writeStaticField(m, stmt, f, from, session, STORE_STATIC_FIELD);
    }

    void writeLoadStaticField(SootMethod m, Stmt stmt, SootField f, Local to, Session session) {
        writeStaticField(m, stmt, f, to, session, LOAD_STATIC_FIELD);
    }

    private void writeStaticField(SootMethod m, Stmt stmt, SootField f, Local var, Session session, PredicateFile loadStaticField) {
        int index = session.calcUnitNumber(stmt);
        String insn = _rep.instruction(m, stmt, session, index);
        String methodId = writeMethod(m);

        String fieldId = writeField(f);
        _db.add(loadStaticField, insn, str(index), _rep.local(m, var), fieldId, methodId);
    }

    void writeLoadArrayIndex(SootMethod m, Stmt stmt, Local base, Local to, Local arrIndex, Session session) {
        writeFieldOrIndex(m, stmt, base, to, arrIndex, session, LOAD_ARRAY_INDEX);
    }

    void writeStoreArrayIndex(SootMethod m, Stmt stmt, Local base, Local from, Local arrIndex, Session session) {
        writeFieldOrIndex(m, stmt, base, from, arrIndex, session, STORE_ARRAY_INDEX);
    }

    private void writeFieldOrIndex(SootMethod m, Stmt stmt, Local base, Local var, Local arrIndex, Session session, PredicateFile predicateFile) {
        int index = session.calcUnitNumber(stmt);
        String insn = _rep.instruction(m, stmt, session, index);
        String methodId = writeMethod(m);

        _db.add(predicateFile, insn, str(index), _rep.local(m, var), _rep.local(m, base), methodId);

        if (arrIndex != null)
            _db.add(ARRAY_INSN_INDEX, insn, _rep.local(m, arrIndex));
    }

    void writeApplicationClass(SootClass application) {
        _db.add(APP_CLASS, writeType(application));
    }

    String writeField(SootField f) {
        String fieldId = _rep.signature(f);
        _db.add(FIELD_SIGNATURE, fieldId, writeType(f.getDeclaringClass()), _rep.simpleName(f), writeType(f.getType()));
        if (f.getTag("VisibilityAnnotationTag") != null) {
            VisibilityAnnotationTag vTag = (VisibilityAnnotationTag) f.getTag("VisibilityAnnotationTag");
            for (AnnotationTag aTag : vTag.getAnnotations()) {
                _db.add(FIELD_ANNOTATION, fieldId, soot.coffi.Util.v().jimpleTypeOfFieldDescriptor(aTag.getType()).getEscapedName());
            }
        }
        return fieldId;
    }

    void writeFieldModifier(SootField f, String modifier) {
        String fieldId = writeField(f);
        _db.add(FIELD_MODIFIER, modifier, fieldId);
    }

    void writeClassModifier(SootClass c, String modifier) {
        String type = c.getName();
        if (c.isInterface()) {
            _db.add(INTERFACE_TYPE, type);
        }
        else {
            _db.add(CLASS_TYPE, type);
        }
        _db.add(CLASS_MODIFIER, modifier, type);
    }

    void writeMethodModifier(SootMethod m, String modifier) {
        String methodId = writeMethod(m);
        _db.add(METHOD_MODIFIER, modifier, methodId);
    }

    void writeReturn(SootMethod m, Stmt stmt, Local l, Session session) {
        int index = session.calcUnitNumber(stmt);
        String insn = _rep.instruction(m, stmt, session, index);
        String methodId = writeMethod(m);

        _db.add(RETURN, insn, str(index), _rep.local(m, l), methodId);
    }

    void writeReturnVoid(SootMethod m, Stmt stmt, Session session) {
        int index = session.calcUnitNumber(stmt);
        String insn = _rep.instruction(m, stmt, session, index);
        String methodId = writeMethod(m);

        _db.add(RETURN_VOID, insn, str(index), methodId);
    }

    // The return var of native methods is exceptional, in that it does not
    // correspond to a return instruction.
    void writeNativeReturnVar(SootMethod m) {
        String methodId = writeMethod(m);

        if (!(m.getReturnType() instanceof VoidType)) {
            String  var = _rep.nativeReturnVar(m);
            _db.add(NATIVE_RETURN_VAR, var, methodId);
            _db.add(VAR_TYPE, var, writeType(m.getReturnType()));
            _db.add(VAR_DECLARING_METHOD, var, methodId);
        }
    }

    void writeGoto(SootMethod m, GotoStmt stmt, Session session) {
        Unit to = stmt.getTarget();
        session.calcUnitNumber(stmt);
        int index = session.getUnitNumber(stmt);
        session.calcUnitNumber(to);
        int indexTo = session.getUnitNumber(to);
        String insn = _rep.instruction(m, stmt, session, index);
        String methodId = writeMethod(m);

        _db.add(GOTO, insn, str(index), str(indexTo), methodId);
    }

    /**
     * If
     */
    void writeIf(SootMethod m, IfStmt stmt, Session session) {
        Unit to = stmt.getTarget();
        // index was already computed earlier
        int index = session.getUnitNumber(stmt);
        session.calcUnitNumber(to);
        int indexTo = session.getUnitNumber(to);
        String insn = _rep.instruction(m, stmt, session, index);
        String methodId = writeMethod(m);

        _db.add(IF, insn, str(index), str(indexTo), methodId);

        Value condStmt = ((IfStmt) stmt).getCondition();
        if (condStmt instanceof ConditionExpr) {
            ConditionExpr condition = (ConditionExpr) condStmt;
            if (condition.getOp1() instanceof Local) {
                Local op1 = (Local) condition.getOp1();
                _db.add(IF_VAR, insn, _rep.local(m, op1));
            }
            if (condition.getOp2() instanceof Local) {
                Local op2 = (Local) condition.getOp2();
                _db.add(IF_VAR, insn, _rep.local(m, op2));
            }
        }
    }

    void writeTableSwitch(SootMethod inMethod, TableSwitchStmt stmt, Session session) {
        int stmtIndex = session.getUnitNumber(stmt);

        Value v = writeImmediate(inMethod, stmt, stmt.getKey(), session);

        if(!(v instanceof Local))
            throw new RuntimeException("Unexpected key for TableSwitch statement " + v + " " + v.getClass());

        Local l = (Local) v;
        String insn = _rep.instruction(inMethod, stmt, session, stmtIndex);
        String methodId = writeMethod(inMethod);

        _db.add(TABLE_SWITCH, insn, str(stmtIndex), _rep.local(inMethod, l), methodId);

        for (int tgIndex = stmt.getLowIndex(), i = 0; tgIndex <= stmt.getHighIndex(); tgIndex++, i++) {
            session.calcUnitNumber(stmt.getTarget(i));
            int indexTo = session.getUnitNumber(stmt.getTarget(i));

            _db.add(TABLE_SWITCH_TARGET, insn, str(tgIndex), str(indexTo));
        }

        session.calcUnitNumber(stmt.getDefaultTarget());
        int defaultIndex = session.getUnitNumber(stmt.getDefaultTarget());

        _db.add(TABLE_SWITCH_DEFAULT, insn, str(defaultIndex));
    }

    void writeLookupSwitch(SootMethod inMethod, LookupSwitchStmt stmt, Session session) {
        int stmtIndex = session.getUnitNumber(stmt);

        Value v = writeImmediate(inMethod, stmt, stmt.getKey(), session);

        if(!(v instanceof Local))
            throw new RuntimeException("Unexpected key for TableSwitch statement " + v + " " + v.getClass());

        Local l = (Local) v;
        String insn = _rep.instruction(inMethod, stmt, session, stmtIndex);
        String methodId = writeMethod(inMethod);

        _db.add(LOOKUP_SWITCH, insn, str(stmtIndex), _rep.local(inMethod, l), methodId);

        for(int i = 0, end = stmt.getTargetCount(); i < end; i++) {
            int tgIndex = stmt.getLookupValue(i);
            session.calcUnitNumber(stmt.getTarget(i));
            int indexTo = session.getUnitNumber(stmt.getTarget(i));

            _db.add(LOOKUP_SWITCH_TARGET, insn, str(tgIndex), str(indexTo));
        }

        session.calcUnitNumber(stmt.getDefaultTarget());
        int defaultIndex = session.getUnitNumber(stmt.getDefaultTarget());

        _db.add(LOOKUP_SWITCH_DEFAULT, insn, str(defaultIndex));
    }

    void writeUnsupported(SootMethod m, Stmt stmt, Session session) {
        int index = session.calcUnitNumber(stmt);
        String insn = _rep.unsupported(m, stmt, index);
        String methodId = writeMethod(m);

        _db.add(UNSUPPORTED_INSTRUCTION, insn, str(index), methodId);
    }

    /**
     * Throw statement
     */
    void writeThrow(SootMethod m, Stmt stmt, Local l, Session session) {
        int index = session.calcUnitNumber(stmt);
        String insn = _rep.throwLocal(m, l, session);
        String methodId = writeMethod(m);

        _db.add(THROW, insn, str(index), _rep.local(m, l), methodId);
    }

    /**
     * Throw null
     */
    void writeThrowNull(SootMethod m, Stmt stmt, Session session) {
        int index = session.calcUnitNumber(stmt);
        String insn = _rep.instruction(m, stmt, session, index);
        String methodId = writeMethod(m);

        _db.add(THROW_NULL, insn, str(index), methodId);
    }

    void writeExceptionHandlerPrevious(SootMethod m, Trap current, Trap previous, Session session) {
        _db.add(EXCEPT_HANDLER_PREV, _rep.handler(m, current, session), _rep.handler(m, previous, session));
    }

    void writeExceptionHandler(SootMethod m, Trap handler, Session session) {
        SootClass exc = handler.getException();

        Local caught;
        {
            Unit handlerUnit = handler.getHandlerUnit();
            IdentityStmt stmt = (IdentityStmt) handlerUnit;
            Value left = stmt.getLeftOp();
            Value right = stmt.getRightOp();

            if (right instanceof CaughtExceptionRef && left instanceof Local) {
                caught = (Local) left;
            }
            else {
                throw new RuntimeException("Unexpected start of exception handler: " + handlerUnit);
            }
        }

        String insn = _rep.handler(m, handler, session);
        int handlerIndex = session.getUnitNumber(handler.getHandlerUnit());
        session.calcUnitNumber(handler.getBeginUnit());
        int beginIndex = session.getUnitNumber(handler.getBeginUnit());
        session.calcUnitNumber(handler.getEndUnit());
        int endIndex = session.getUnitNumber(handler.getEndUnit());
        _db.add(EXCEPTION_HANDLER, insn, _rep.signature(m), str(handlerIndex), exc.getName(), _rep.local(m, caught), str(beginIndex), str(endIndex));
    }

    void writeThisVar(SootMethod m) {
        String methodId = writeMethod(m);
        String thisVar = _rep.thisVar(m);
        _db.add(THIS_VAR, methodId, thisVar);
        _db.add(VAR_TYPE, thisVar, writeType(m.getDeclaringClass()));
        _db.add(VAR_DECLARING_METHOD, thisVar, methodId);
    }

    void writeMethodDeclaresException(SootMethod m, SootClass exception) {
        _db.add(METHOD_DECL_EXCEPTION, writeType(exception), writeMethod(m));
    }

    void writeFormalParam(SootMethod m, int i) {
        String methodId = writeMethod(m);
        String var = _rep.param(m, i);
        _db.add(FORMAL_PARAM, str(i), methodId, var);
        _db.add(VAR_TYPE, var, writeType(m.getParameterType(i)));
        _db.add(VAR_DECLARING_METHOD, var, methodId);
    }

    void writeLocal(SootMethod m, Local l) {
        String local = _rep.local(m, l);
        Type type;

        if (_varTypeMap.containsKey(local))
            type = _varTypeMap.get(local);
        else {
            type = l.getType();
            _varTypeMap.put(local, type);
        }

        _db.add(VAR_TYPE, local, writeType(type));
        _db.add(VAR_DECLARING_METHOD, local, writeMethod(m));
    }

    Local writeStringConstantExpression(SootMethod inMethod, Stmt stmt, StringConstant constant, Session session) {
        // introduce a new temporary variable
        String basename = "$stringconstant";
        String varname = basename + session.nextNumber(basename);
        Local l = new JimpleLocal(varname, RefType.v("java.lang.String"), -1, -1);
        writeLocal(inMethod, l);
        writeAssignStringConstant(inMethod, stmt, l, constant, session);
        return l;
    }

    Local writeNullExpression(SootMethod inMethod, Stmt stmt, Type type, Session session) {
        // introduce a new temporary variable
        String basename = "$null";
        String varname = basename + session.nextNumber(basename);
        Local l = new JimpleLocal(varname, type, -1, -1);
        writeLocal(inMethod, l);
        writeAssignNull(inMethod, stmt, l, session);
        return l;
    }

    Local writeNumConstantExpression(SootMethod inMethod, Stmt stmt, NumericConstant constant, Session session) {
        // introduce a new temporary variable
        String basename = "$numconstant";
        String varname = basename + session.nextNumber(basename);
        Local l = new JimpleLocal(varname, constant.getType(), -1, -1);
        writeLocal(inMethod, l);
        writeAssignNumConstant(inMethod, stmt, l, constant, session);
        return l;
    }

    Local writeClassConstantExpression(SootMethod inMethod, Stmt stmt, ClassConstant constant, Session session) {
        // introduce a new temporary variable
        String basename = "$classconstant";
        String varname = basename + session.nextNumber(basename);
        Local l = new JimpleLocal(varname, RefType.v("java.lang.Class"), -1, -1);
        writeLocal(inMethod, l);
        writeAssignClassConstant(inMethod, stmt, l, constant, session);
        return l;
    }

    private Local writeMethodHandleConstantExpression(SootMethod inMethod, Stmt stmt, MethodHandle constant, Session session) {
        // introduce a new temporary variable
        String basename = "$mhandleconstant";
        String varname = basename + session.nextNumber(basename);
        Local l = new JimpleLocal(varname, RefType.v("java.lang.invoke.MethodHandle"), -1, -1);
        writeLocal(inMethod, l);
        writeAssignMethodHandleConstant(inMethod, stmt, l, constant, session);
        return l;
    }

    private Value writeActualParam(SootMethod inMethod, Stmt stmt, InvokeExpr expr, Session session, Value v, int idx) {
	if (v instanceof StringConstant)
	    return writeStringConstantExpression(inMethod, stmt, (StringConstant) v, session);
	else if (v instanceof ClassConstant)
	    return writeClassConstantExpression(inMethod, stmt, (ClassConstant) v, session);
	else if (v instanceof NumericConstant)
	    return writeNumConstantExpression(inMethod, stmt, (NumericConstant) v, session);
	else if (v instanceof MethodHandle)
	    return writeMethodHandleConstantExpression(inMethod, stmt, (MethodHandle) v, session);
	else if (v instanceof NullConstant) {
	    // Giving the type of the formal argument to be used in the creation of
	    // temporary var for the actual argument (whose value is null).
	    Type argType = expr.getMethodRef().parameterType(idx);
	    return writeNullExpression(inMethod, stmt, argType, session);
	}
	else if (v instanceof Constant)
	    throw new RuntimeException("Value has unknown constant type: " + v);
	return v;
    }

    private void writeActualParams(SootMethod inMethod, Stmt stmt, InvokeExpr expr, String invokeExprRepr, Session session) {
        for(int i = 0; i < expr.getArgCount(); i++) {
            Value v = writeActualParam(inMethod, stmt, expr, session, expr.getArg(i), i);

            if (v instanceof Local) {
                Local l = (Local) v;
                _db.add(ACTUAL_PARAMETER, str(i), invokeExprRepr, _rep.local(inMethod, l));
            }
            else {
                throw new RuntimeException("Actual parameter is not a local: " + v + " " + v.getClass());
            }
        }
        if (expr instanceof DynamicInvokeExpr) {
            DynamicInvokeExpr di = (DynamicInvokeExpr)expr;
            for (int j = 0; j < di.getBootstrapArgCount(); j++) {
                Value v = di.getBootstrapArg(j);
                if (v instanceof Constant) {
                    Value vConst = writeActualParam(inMethod, stmt, expr, session, (Value)v, j);
                    if (vConst instanceof Local) {
                        Local l = (Local) vConst;
                        _db.add(BOOTSTRAP_PARAMETER, str(j), invokeExprRepr, _rep.local(inMethod, l));
                    }
                    else {
                        throw new RuntimeException("Unknown actual parameter: " + v + " of type " + v.getClass().getName());
                    }
                }
                else {
                    throw new RuntimeException("Found non-constant argument to bootstrap method: " + di);
                }
            }
        }
    }

    void writeInvoke(SootMethod inMethod, Stmt stmt, Session session) {
        InvokeExpr expr = stmt.getInvokeExpr();
        writeInvokeHelper(inMethod, stmt, expr, session);
    }

    private String writeInvokeHelper(SootMethod inMethod, Stmt stmt, InvokeExpr expr, Session session) {
        int index = session.calcUnitNumber(stmt);
        String insn = _rep.invoke(inMethod, expr, session);
        String methodId = writeMethod(inMethod);

        writeActualParams(inMethod, stmt, expr, insn, session);

        LineNumberTag tag = (LineNumberTag) stmt.getTag("LineNumberTag");
        if (tag != null) {
            _db.add(METHOD_INV_LINE, insn, str(tag.getLineNumber()));
        }

        if (expr instanceof StaticInvokeExpr) {
            _db.add(STATIC_METHOD_INV, insn, str(index), _rep.signature(expr.getMethod()), methodId);
        }
        else if (expr instanceof VirtualInvokeExpr || expr instanceof InterfaceInvokeExpr) {
            _db.add(VIRTUAL_METHOD_INV, insn, str(index), _rep.signature(expr.getMethod()), _rep.local(inMethod, (Local) ((InstanceInvokeExpr) expr).getBase()), methodId);
        }
        else if (expr instanceof SpecialInvokeExpr) {
            _db.add(SPECIAL_METHOD_INV, insn, str(index), _rep.signature(expr.getMethod()), _rep.local(inMethod, (Local) ((InstanceInvokeExpr) expr).getBase()), methodId);
        }
        else if (expr instanceof DynamicInvokeExpr) {
            writeDynamicInvoke((DynamicInvokeExpr)expr, index, insn, methodId);
        }
        else {
            throw new RuntimeException("Cannot handle invoke expr: " + expr);
        }

        return insn;
    }

    private String getBootstrapSig(DynamicInvokeExpr di) {
        SootMethodRef bootstrapMeth = di.getBootstrapMethodRef();
        if (bootstrapMeth.declaringClass().isPhantom()) {
            String bootstrapSig = bootstrapMeth.toString();
            System.out.println("Bootstrap method is phantom: " + bootstrapSig);
            _db.add(PHANTOM_METHOD, bootstrapSig);
            return bootstrapSig;
        } else
            return _rep.signature(bootstrapMeth.resolve());
    }

    private void writeDynamicInvoke(DynamicInvokeExpr di, int index, String insn, String methodId) {
        SootMethodRef dynInfo = di.getMethodRef();
        String dynArity = String.valueOf(dynInfo.parameterTypes().size());
        _db.add(DYNAMIC_METHOD_INV, insn, str(index), getBootstrapSig(di), dynInfo.name(), dynInfo.returnType().toString(), dynArity, dynInfo.parameterTypes().toString(), methodId);
    }

    private Value writeImmediate(SootMethod inMethod, Stmt stmt, Value v, Session session) {
        if (v instanceof StringConstant)
            v = writeStringConstantExpression(inMethod, stmt, (StringConstant) v, session);
        else if (v instanceof ClassConstant)
            v = writeClassConstantExpression(inMethod, stmt, (ClassConstant) v, session);
        else if (v instanceof NumericConstant)
            v = writeNumConstantExpression(inMethod, stmt, (NumericConstant) v, session);

        return v;
    }

    void writeAssignBinop(SootMethod m, AssignStmt stmt, Local left, BinopExpr right, Session session) {
        int index = session.calcUnitNumber(stmt);
        String insn = _rep.instruction(m, stmt, session, index);
        String methodId = writeMethod(m);

        _db.add(ASSIGN_BINOP, insn, str(index), _rep.local(m, left), methodId);

        if (right.getOp1() instanceof Local) {
            Local op1 = (Local) right.getOp1();
            _db.add(ASSIGN_OPER_FROM, insn, _rep.local(m, op1));
        }

        if (right.getOp2() instanceof Local) {
            Local op2 = (Local) right.getOp2();
            _db.add(ASSIGN_OPER_FROM, insn, _rep.local(m, op2));
        }
    }

    void writeAssignUnop(SootMethod m, AssignStmt stmt, Local left, UnopExpr right, Session session) {
        int index = session.calcUnitNumber(stmt);
        String insn = _rep.instruction(m, stmt, session, index);
        String methodId = writeMethod(m);

        _db.add(ASSIGN_UNOP, insn, str(index), _rep.local(m, left), methodId);

        if (right.getOp() instanceof Local) {
            Local op = (Local) right.getOp();
            _db.add(ASSIGN_OPER_FROM, insn, _rep.local(m, op));
        }
    }

    void writeAssignInstanceOf(SootMethod m, AssignStmt stmt, Local to, Local from, Type t, Session session) {
        int index = session.calcUnitNumber(stmt);
        String insn = _rep.instruction(m, stmt, session, index);
        String methodId = writeMethod(m);

        _db.add(ASSIGN_INSTANCE_OF, insn, str(index), _rep.local(m, from), _rep.local(m, to), writeType(t), methodId);
    }

    void writeAssignPhantomInvoke(SootMethod m, AssignStmt stmt, Session session) {
        int index = session.calcUnitNumber(stmt);
        String insn = _rep.instruction(m, stmt, session, index);
        String methodId = writeMethod(m);

        _db.add(ASSIGN_PHANTOM_INVOKE, insn, str(index), methodId);
    }

    void writeBreakpointStmt(SootMethod m, Stmt stmt, Session session) {
        int index = session.calcUnitNumber(stmt);
        String insn = _rep.instruction(m, stmt, session, index);
        String methodId = writeMethod(m);

        _db.add(BREAKPOINT_STMT, insn, str(index), methodId);
    }

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

    void writeFieldInitialValue(SootField f) {
        String fieldId = _rep.signature(f);
        String valueString = f.getInitialValueString();
        if (valueString != null && !valueString.equals("")) {
            int pos = valueString.indexOf('@');
            if (pos < 0)
                System.err.println("Unexpected format (no @) in initial field value");
            else {
                try {
                    int value = (int) Long.parseLong(valueString.substring(pos+1), 16); // parse hex string, possibly negative int
                    _db.add(FIELD_INITIAL_VALUE, fieldId, Integer.toString(value));
                } catch (NumberFormatException e) {
                    _db.add(FIELD_INITIAL_VALUE, fieldId, valueString.substring(pos+1));
                    // if we failed to parse the value as a hex int, output it in full
                }
            }
        }
    }
}
