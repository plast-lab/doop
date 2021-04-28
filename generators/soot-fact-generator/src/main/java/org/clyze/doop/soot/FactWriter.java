package org.clyze.doop.soot;

import com.google.common.collect.Lists;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.clyze.doop.common.BasicJavaSupport;
import org.clyze.doop.common.Database;
import org.clyze.doop.common.JavaFactWriter;
import org.clyze.doop.common.Phantoms;
import org.clyze.doop.common.PredicateFile;
import org.clyze.doop.common.SessionCounter;
import org.clyze.utils.TypeUtils;
import soot.*;
import soot.jimple.*;
import soot.jimple.internal.JimpleLocal;
import soot.jimple.toolkits.typing.fast.BottomType;
import soot.tagkit.*;
import soot.util.backend.ASMBackendUtils;

import static org.clyze.doop.common.JavaRepresentation.*;
import static org.clyze.doop.common.PredicateFile.*;

/**
 * FactWriter determines the format of a fact and adds it to a
 * database.
 */
class FactWriter extends JavaFactWriter {
    private final Representation _rep;
    private final Map<String, Type> _varTypeMap = new ConcurrentHashMap<>();
    private final Phantoms phantoms;
    private final Collection<Object> seenPhantoms = new HashSet<>();

    FactWriter(Database db, SootParameters params, Representation rep, Phantoms phantoms) {
        super(db, params);
        this._rep = rep;
        this.phantoms = phantoms;
    }

    private String methodSig(SootMethod m, String methodRaw) {
        if (methodRaw == null)
            methodRaw = _rep.signature(m);
        return methodRaw; // hashMethodNameIfLong(methodRaw);
    }

    private static String getAnnotationType(AnnotationTag aTag) {
        return TypeUtils.raiseTypeId(aTag.getType());
    }

    void writeMethod(SootMethod m) {
        String methodRaw = _rep.signature(m);
        String methodId = methodSig(m, methodRaw);
        String arity = Integer.toString(m.getParameterCount());

        _db.add(STRING_RAW, methodId, methodRaw);
        writeMethod(methodId, _rep.simpleName(m), Representation.params(m), writeType(m.getDeclaringClass()), writeType(m.getReturnType()), ASMBackendUtils.toTypeDesc(m.makeRef()), arity);
        if (m.getTag("VisibilityAnnotationTag") != null) {
            VisibilityAnnotationTag vTag = (VisibilityAnnotationTag) m.getTag("VisibilityAnnotationTag");
            for (AnnotationTag aTag : vTag.getAnnotations()) {
                writeMethodAnnotation(methodId, getAnnotationType(aTag));
                writeAnnotationElements("method", methodId, null, aTag.getElems());
            }
        }
        if (m.getTag("VisibilityParameterAnnotationTag") != null) {
            VisibilityParameterAnnotationTag vTag = (VisibilityParameterAnnotationTag) m.getTag("VisibilityParameterAnnotationTag");

            ArrayList<VisibilityAnnotationTag> annList = vTag.getVisibilityAnnotations();
            for (int i = 0; i < annList.size(); i++)
                if (annList.get(i) != null)
                    for (AnnotationTag aTag : annList.get(i).getAnnotations()) {
                        String paramIdx = str(i);
                        _db.add(PARAM_ANNOTATION, methodId, paramIdx, getAnnotationType(aTag));
                        String paramId = methodId + "::parameter#" + paramIdx;
                        writeAnnotationElements("param", paramId, null, aTag.getElems());
                    }

        }
    }

    void writeAndroidEntryPoint(SootMethod m) {
        _db.add(ANDROID_ENTRY_POINT, _rep.signature(m));
    }

    void writeClassOrInterfaceType(SootClass c) {
        String classStr = c.getName();
        if (c.isPhantom()) {
            phantoms.reportPhantom("Interface", classStr);
            writePhantomType(c);
        }
        _db.add(c.isInterface() ? INTERFACE_TYPE : CLASS_TYPE, classStr);
        writeClassHeap(Representation.classConstant(c), classStr);
        if (c.getTag("VisibilityAnnotationTag") != null) {
            VisibilityAnnotationTag vTag = (VisibilityAnnotationTag) c.getTag("VisibilityAnnotationTag");
            for (AnnotationTag aTag : vTag.getAnnotations()) {
                _db.add(TYPE_ANNOTATION, classStr, getAnnotationType(aTag));
                writeAnnotationElements("type", classStr, null, aTag.getElems());
            }
        }
        _db.add(TYPE_SIMPLENAME, classStr, c.getShortName());
    }

    /**
     * Write an annotation element. Annotation elements may be nested, so we
     * have to generate facts that describe their structure as well.
     *
     * @param targetType  the type of the language construct that is annotated (e.g., "method")
     * @param target      the language construct that is annotated
     * @param parentId    the ID of the parent of the annotation (or "0" for root)
     * @param thisId      the annotation ID (e.g., an index to distinguish from siblings)
     * @param ae          the annotation element
     */
    private void writeAnnotationElement(String targetType, String target, String parentId, String thisId, AnnotationElem ae) {
        if (ae instanceof AnnotationArrayElem) {
            writeAnnotationElements(targetType, target, thisId, ((AnnotationArrayElem)ae).getValues());
        } else if (ae instanceof AnnotationEnumElem) {
            AnnotationEnumElem enumElem = (AnnotationEnumElem)ae;
            writeAnnotationElement(targetType, target, parentId, thisId, enumElem.getName(), TypeUtils.raiseTypeId(enumElem.getTypeName()), enumElem.getConstantName());
        } else if (ae instanceof AnnotationStringElem) {
            AnnotationStringElem ase = (AnnotationStringElem)ae;
            writeAnnotationElement(targetType, target, parentId, thisId, ase.getName(), ase.getValue(), "-");
        } else if (ae instanceof AnnotationBooleanElem) {
            AnnotationBooleanElem abe = (AnnotationBooleanElem)ae;
            writeAnnotationElement(targetType, target, parentId, thisId, abe.getName(), String.valueOf(abe.getValue()), "-");
        } else if (ae instanceof AnnotationFloatElem) {
            AnnotationFloatElem afe = (AnnotationFloatElem)ae;
            writeAnnotationElement(targetType, target, parentId, thisId, afe.getName(), String.valueOf(afe.getValue()), "-");
        } else if (ae instanceof AnnotationLongElem) {
            AnnotationLongElem ale = (AnnotationLongElem)ae;
            writeAnnotationElement(targetType, target, parentId, thisId, ale.getName(), String.valueOf(ale.getValue()), "-");
        } else if (ae instanceof AnnotationDoubleElem) {
            AnnotationDoubleElem ade = (AnnotationDoubleElem)ae;
            writeAnnotationElement(targetType, target, parentId, thisId, ade.getName(), String.valueOf(ade.getValue()), "-");
        } else if (ae instanceof AnnotationIntElem) {
            AnnotationIntElem aie = (AnnotationIntElem)ae;
            writeAnnotationElement(targetType, target, parentId, thisId, aie.getName(), str(aie.getValue()), "-");
        } else if (ae instanceof AnnotationClassElem) {
            AnnotationClassElem ace = (AnnotationClassElem)ae;
            writeAnnotationElement(targetType, target, parentId, thisId, ace.getName(), TypeUtils.raiseTypeId(ace.getDesc()), "-");
        } else if (ae instanceof AnnotationAnnotationElem) {
            AnnotationAnnotationElem aae = (AnnotationAnnotationElem)ae;
            // Write a dummy annotation node, followed by its contents.
            writeAnnotationElement(targetType, target, parentId, thisId, "INNER-ANNOTATION", "-", null);
            writeAnnotationElements(targetType, target, thisId, aae.getValue().getElems());
        } else
            System.err.println("WARNING: unknown annotation element, type: '" + ae.getClass() + "', name: '" + ae.getName() + "'");
    }

    // Helper method used by writeAnnotationElement().
    private void writeAnnotationElements(String targetType, String target, String parentId,
                                         Iterable<AnnotationElem> elements) {
        if (parentId == null)
            parentId = "0";
        int id = 0;
        for (AnnotationElem ae : elements)
            writeAnnotationElement(targetType, target, parentId, parentId + "." + (id++), ae);
    }

    void writeDirectSuperclass(SootClass sub, SootClass sup) {
        _db.add(DIRECT_SUPER_CLASS, writeType(sub), writeType(sup));
    }

    void writeDirectSuperinterface(SootClass clazz, SootClass iface) {
        _db.add(DIRECT_SUPER_IFACE, writeType(clazz), writeType(iface));
    }

    private String writeType(SootClass c) {
        // The type itself is already taken care of by writing the
        // SootClass declaration, so we don't actually write the type
        // here, and just return the string.
        return c.getName();
    }

    private String writeType(Type t) {
        String result = t.toString();

        if (t instanceof ArrayType) {
            Type componentType = ((ArrayType) t).getElementType();
            writeArrayTypes(result, writeType(componentType));
        }
        else if (t instanceof PrimType || t instanceof NullType ||
                t instanceof RefType || t instanceof VoidType || t instanceof BottomType) {
            // taken care of by the standard facts
        }
        else
            throw new RuntimeException("Don't know what to do with type " + t);

        return result;
    }

    void writePhantomType(Type t) {
        phantoms.reportPhantom("Type", t.toString());
        writePhantomType(writeType(t));
    }

    private void writePhantomType(SootClass c) {
        writePhantomType(writeType(c));
    }

    void writePhantomMethod(SootMethod m) {
        String sig = methodSig(m, null);
        phantoms.reportPhantom("Method", sig);
        writePhantomMethod(sig);
    }

    void writePhantomBasedMethod(SootMethod m) {
        String sig = methodSig(m, null);
        phantoms.reportPhantomSignature(sig);
        _db.add(PHANTOM_BASED_METHOD, sig);
    }

    @SuppressWarnings("TypeMayBeWeakened")
    void writeEnterMonitor(SootMethod m, EnterMonitorStmt stmt, Local var, Session session) {
        InstrInfo ii = calcInstrInfo(m, stmt, session);
        _db.add(ENTER_MONITOR, ii.insn, str(ii.index), _rep.local(m, var), ii.methodId);
    }

    @SuppressWarnings("TypeMayBeWeakened")
    void writeExitMonitor(SootMethod m, ExitMonitorStmt stmt, Local var, Session session) {
        InstrInfo ii = calcInstrInfo(m, stmt, session);
        _db.add(EXIT_MONITOR, ii.insn, str(ii.index), _rep.local(m, var), ii.methodId);
    }

    void writeAssignLocal(SootMethod m, Stmt stmt, Local to, Local from, Session session) {
        InstrInfo ii = calcInstrInfo(m, stmt, session);
        writeAssignLocal(ii.insn, ii.index, _rep.local(m, from), _rep.local(m, to), ii.methodId);
    }

    void writeAssignThisToLocal(SootMethod m, Stmt stmt, Local to, Session session) {
        InstrInfo ii = calcInstrInfo(m, stmt, session);
        writeAssignLocal(ii.insn, ii.index, _rep.thisVar(m), _rep.local(m, to), ii.methodId);
    }

    void writeAssignLocal(SootMethod m, Stmt stmt, Local to, ParameterRef ref, Session session) {
        InstrInfo ii = calcInstrInfo(m, stmt, session);
        writeAssignLocal(ii.insn, ii.index, _rep.param(m, ref.getIndex()), _rep.local(m, to), ii.methodId);
    }

    void writeAssignInvoke(SootMethod inMethod, Stmt stmt, Local to, InvokeExpr expr, Session session) {
        String insn = writeInvokeHelper(inMethod, stmt, expr, session);
        _db.add(ASSIGN_RETURN_VALUE, insn, _rep.local(inMethod, to));
    }

    private void writeArraySize(SootMethod m, InstrInfo ii, Value sizeVal, int pos, String heap) {
        if (sizeVal instanceof IntConstant) {
            IntConstant size = (IntConstant) sizeVal;
            _db.add(ARRAY_ALLOC_CONST_SIZE, ii.insn, str(pos), str(size.value));
            if(size.value == 0) _db.add(EMPTY_ARRAY, heap);
        }
        else if (sizeVal instanceof Local)
            _db.add(ARRAY_ALLOC, ii.insn, str(pos), _rep.local(m, (Local)sizeVal));
    }

    void writeAssignHeapAllocation(SootMethod m, Stmt stmt, Local l, Value expr, Session session) {
        String heap = _rep.heapAlloc(m, expr, session);
        _db.add(NORMAL_HEAP, heap, writeType(expr.getType()));

        // statement
        InstrInfo ii = calcInstrInfo(m, stmt, session);
        _db.add(ASSIGN_HEAP_ALLOC, ii.insn, str(ii.index), heap, _rep.local(m, l), ii.methodId, ""+getLineNumberFromStmt(stmt));

        if (expr instanceof NewArrayExpr) {
            NewArrayExpr newArray = (NewArrayExpr) expr;
            writeArraySize(m, ii, newArray.getSize(), 0, heap);
        }
    }

    private static int getLineNumberFromStmt(Stmt stmt) {
        LineNumberTag tag = (LineNumberTag) stmt.getTag("LineNumberTag");
        return tag == null ? 0 : tag.getLineNumber();
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
        writeAssignNewMultiArrayExprHelper(m, stmt, l, _rep.local(m,l), expr, (ArrayType) expr.getType(), session, 0);
    }

    private void writeAssignNewMultiArrayExprHelper(SootMethod m, Stmt stmt, Local l, String assignTo, NewMultiArrayExpr expr, ArrayType arrayType, Session session, int pos) {
        String heap = _rep.heapMultiArrayAlloc(m, /* expr, */ arrayType, session);
        InstrInfo ii = calcInstrInfo(m, stmt, session);
        String methodId = ii.methodId;

        _db.add(NORMAL_HEAP, heap, writeType(arrayType));
        _db.add(ASSIGN_HEAP_ALLOC, ii.insn, str(ii.index), heap, assignTo, methodId, ""+getLineNumberFromStmt(stmt));
        if (pos < expr.getSizeCount())
            writeArraySize(m, ii, expr.getSize(pos), pos, heap);

        Type componentType = getComponentType(arrayType);
        if (componentType instanceof ArrayType) {
            String childAssignTo = _rep.newLocalIntermediate(m, l, session);
            writeAssignNewMultiArrayExprHelper(m, stmt, l, childAssignTo, expr, (ArrayType) componentType, session, pos+1);
            int storeInsnIndex = session.calcUnitNumber(stmt);
            String storeInsn = _rep.instruction(m, stmt, storeInsnIndex);

            _db.add(STORE_ARRAY_INDEX, storeInsn, str(storeInsnIndex), childAssignTo, assignTo, methodId);
            writeLocal(childAssignTo, writeType(componentType), methodId);
            _db.add(ARRAY_INSN_INDEX, ii.insn, childAssignTo);
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
        String rep = _rep.instruction(m, stmt, index);

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
        InstrInfo ii = calcInstrInfo(m, stmt, session);
        _db.add(ASSIGN_HEAP_ALLOC, ii.insn, str(ii.index), heapId, _rep.local(m, l), ii.methodId, ""+getLineNumberFromStmt(stmt));
    }

    void writeAssignNull(SootMethod m, Stmt stmt, Local l, Session session) {
        InstrInfo ii = calcInstrInfo(m, stmt, session);
        _db.add(ASSIGN_NULL, ii.insn, str(ii.index), _rep.local(m, l), ii.methodId);
    }

    void writeAssignNumConstant(SootMethod m, Stmt stmt, Local l, NumericConstant constant, Session session) {
        InstrInfo ii = calcInstrInfo(m, stmt, session);
        _db.add(ASSIGN_NUM_CONST, ii.insn, str(ii.index), constant.toString(), _rep.local(m, l), ii.methodId);
    }

    private void writeAssignMethodHandleConstant(SootMethod m, Stmt stmt, Local l, MethodHandle constant, Session session) {
        InstrInfo ii = calcInstrInfo(m, stmt, session);
        String handleMethod = Representation.signature(constant.getMethodRef());
        String heap = methodHandleConstant(handleMethod);
        SigInfo si = new SigInfo(constant.getMethodRef(), false);

        writeMethodHandleConstant(heap, handleMethod, si.retType, si.paramTypes, si.arity);
        _db.add(ASSIGN_HEAP_ALLOC, ii.insn, str(ii.index), heap, _rep.local(m, l), ii.methodId, "0");
    }

    private void writeAssignMethodTypeConstant(SootMethod m, Stmt stmt, Local l, DoopAddons.MethodType constant, Session session) {
        InstrInfo ii = calcInstrInfo(m, stmt, session);
        String retType = constant.getReturnType();
        List<String> paramTypesList = constant.getParameterTypes();
        int arity = paramTypesList.size();
        String[] paramTypes = new String[arity];
	paramTypes = paramTypesList.toArray(paramTypes);
        writeMethodTypeConstant(retType, paramTypes, null);
        String params = concatenate(paramTypes);
        String mt = "<method type (" + params + ")" + retType + ">";
        _db.add(ASSIGN_HEAP_ALLOC, ii.insn, str(ii.index), mt, _rep.local(m, l), ii.methodId, "0");
    }

    void writeAssignClassConstant(SootMethod m, Stmt stmt, Local l, ClassConstant constant, Session session) {
        writeAssignClassConstant(m, stmt, l, new ClassConstantInfo(constant), session);
    }

    private void writeAssignClassConstant(SootMethod m, Stmt stmt, Local l, ClassConstantInfo info, Session session) {
        if (info.isMethodType)
            writeMethodTypeConstant(info.heap);
        else
            writeClassHeap(info.heap, info.actualType);

        InstrInfo ii = calcInstrInfo(m, stmt, session);

        // REVIEW: the class object is not explicitly written. Is this always ok?
        _db.add(ASSIGN_HEAP_ALLOC, ii.insn, str(ii.index), info.heap, _rep.local(m, l), ii.methodId, "0");
    }

    void writeAssignCast(SootMethod m, Stmt stmt, Local to, Local from, Type t, Session session) {
        InstrInfo ii = calcInstrInfo(m, stmt, session);
        _db.add(ASSIGN_CAST, ii.insn, str(ii.index), _rep.local(m, from), _rep.local(m, to), writeType(t), ii.methodId);
    }

    void writeAssignCastNumericConstant(SootMethod m, Stmt stmt, Local to, NumericConstant constant, Type t, Session session) {
        InstrInfo ii = calcInstrInfo(m, stmt, session);
        String val = constant.toString();
        if (constant instanceof ArithmeticConstant)
            writeNumConstantRawInt(val);
        _db.add(ASSIGN_CAST_NUM_CONST, ii.insn, str(ii.index), val, _rep.local(m, to), writeType(t), ii.methodId);
    }

    void writeAssignCastNull(SootMethod m, Stmt stmt, Local to, Type t, Session session) {
        InstrInfo ii = calcInstrInfo(m, stmt, session);
        _db.add(ASSIGN_CAST_NULL, ii.insn, str(ii.index), _rep.local(m, to), writeType(t), ii.methodId);
    }

    void writeStoreInstanceField(SootMethod m, Stmt stmt, SootField f, Local base, Local from, Session session) {
        writeInstanceField(m, stmt, f, base, from, session, STORE_INST_FIELD);
    }

    void writeLoadInstanceField(SootMethod m, Stmt stmt, SootField f, Local base, Local to, Session session) {
        writeInstanceField(m, stmt, f, base, to, session, LOAD_INST_FIELD);
    }

    private void writeInstanceField(SootMethod m, Stmt stmt, SootField f, Local base, Local var, Session session, PredicateFile storeOrLoadInstField) {
        InstrInfo ii = calcInstrInfo(m, stmt, session);
        String fieldId = writeField(f);
        if (fieldId != null)
            _db.add(storeOrLoadInstField, ii.insn, str(ii.index), _rep.local(m, var), _rep.local(m, base), fieldId, ii.methodId);
    }

    void writeStoreStaticField(SootMethod m, Stmt stmt, SootField f, Local from, Session session) {
        writeStaticField(m, stmt, f, from, session, STORE_STATIC_FIELD);
    }

    void writeLoadStaticField(SootMethod m, Stmt stmt, SootField f, Local to, Session session) {
        writeStaticField(m, stmt, f, to, session, LOAD_STATIC_FIELD);
    }

    private void writeStaticField(SootMethod m, Stmt stmt, SootField f, Local var, Session session, PredicateFile staticFieldFacts) {
        InstrInfo ii = calcInstrInfo(m, stmt, session);
        String fieldId = writeField(f);
        if (fieldId != null)
            _db.add(staticFieldFacts, ii.insn, str(ii.index), _rep.local(m, var), fieldId, ii.methodId);
    }

    void writeLoadArrayIndex(SootMethod m, Stmt stmt, Local base, Local to, Value arrIndex, Session session) {
        writeLoadOrStoreArrayIndex(m, stmt, base, to, arrIndex, session, LOAD_ARRAY_INDEX);
    }

    void writeStoreArrayIndex(SootMethod m, Stmt stmt, Local base, Local from, Value arrIndex, Session session) {
        writeLoadOrStoreArrayIndex(m, stmt, base, from, arrIndex, session, STORE_ARRAY_INDEX);
    }

    private void writeLoadOrStoreArrayIndex(SootMethod m, Stmt stmt, Local base, Local var, Value arrIndex, Session session, PredicateFile predicateFile) {
        InstrInfo ii = calcInstrInfo(m, stmt, session);
        _db.add(predicateFile, ii.insn, str(ii.index), _rep.local(m, var), _rep.local(m, base), ii.methodId);

        if(arrIndex instanceof Local)
        {
            _db.add(ARRAY_INSN_INDEX, ii.insn, _rep.local(m, (Local) arrIndex));
        }
        else if(arrIndex instanceof IntConstant)
        {
            _db.add(ARRAY_NUM_INDEX, ii.insn, str(((IntConstant) arrIndex).value));
        }
        else
        {
            throw new RuntimeException("Cannot handle assignment: " + stmt + " (index: " + arrIndex.getClass() + ")");
        }
    }

    private void writeApplicationClass(SootClass application) {
        _db.add(APP_CLASS, writeType(application));
    }

    String writeField(SootField f) {
        if (f == null) {
            System.err.println("WARNING: null field encountered.");
            return null;
        }
        String fieldId = _rep.signature(f);
        _db.add(FIELD_SIGNATURE, fieldId, writeType(f.getDeclaringClass()), Representation.simpleName(f), writeType(f.getType()));
        if (f.getTag("VisibilityAnnotationTag") != null) {
            VisibilityAnnotationTag vTag = (VisibilityAnnotationTag) f.getTag("VisibilityAnnotationTag");
            for (AnnotationTag aTag : vTag.getAnnotations()) {
                _db.add(FIELD_ANNOTATION, fieldId, getAnnotationType(aTag));
                writeAnnotationElements("field", fieldId, null, aTag.getElems());
            }
        }
        return fieldId;
    }

    void writeFieldModifier(SootField f, String modifier) {
        String fieldId = _rep.signature(f);
        _db.add(FIELD_MODIFIER, modifier, fieldId);
    }

    void writeClassModifier(SootClass c, String modifier) {
        writeClassModifier(c.getName(), modifier);
    }

    void writeMethodModifier(SootMethod m, String modifier) {
        _db.add(METHOD_MODIFIER, modifier, methodSig(m, null));
    }

    void writeReturn(SootMethod m, Stmt stmt, Local l, Session session) {
        InstrInfo ii = calcInstrInfo(m, stmt, session);
        _db.add(RETURN, ii.insn, str(ii.index), _rep.local(m, l), ii.methodId);
    }

    void writeReturnVoid(SootMethod m, Stmt stmt, Session session) {
        InstrInfo ii = calcInstrInfo(m, stmt, session);
        _db.add(RETURN_VOID, ii.insn, str(ii.index), ii.methodId);
    }

    // The return var of native methods is exceptional, in that it does not
    // correspond to a return instruction.
    void writeNativeReturnVar(SootMethod m) {
        if (!(m.getReturnType() instanceof VoidType)) {
            String var = _rep.nativeReturnVar(m);
            String methodId = methodSig(m, null);
            _db.add(NATIVE_RETURN_VAR, var, methodId);
            writeLocal(var, writeType(m.getReturnType()), methodId);
        }
    }

    void writeNativeMethodId(SootMethod m) {
        writeNativeMethodId(methodSig(m, null), m.getDeclaringClass().toString(), _rep.simpleName(m));
    }

    void writeGoto(SootMethod m, GotoStmt stmt, Session session) {
        Unit to = stmt.getTarget();
        session.calcUnitNumber(stmt);
        session.calcUnitNumber(to);
        int indexTo = session.getUnitNumber(to);
        InstrInfo ii = getInstrInfo(m, stmt, session);
        _db.add(GOTO, ii.insn, str(ii.index), str(indexTo), ii.methodId);
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
        String insn = _rep.instruction(m, stmt, index);

        writeIf(insn, index, indexTo, methodSig(m, null));

        Value condStmt = stmt.getCondition();
        if (condStmt instanceof ConditionExpr) {
            ConditionExpr condition = (ConditionExpr) condStmt;

            Local dummy = new JimpleLocal("tmp" + insn, BooleanType.v());
            writeDummyIfVar(insn, _rep.local(m, dummy));

            if (condition instanceof EqExpr)
                writeOperatorAt(insn, "==");
            else if (condition instanceof NeExpr)
                writeOperatorAt(insn, "!=");
            else if (condition instanceof GeExpr)
                writeOperatorAt(insn, ">=");
            else if (condition instanceof GtExpr)
                writeOperatorAt(insn, ">");
            else if (condition instanceof LeExpr)
                writeOperatorAt(insn, "<=");
            else if (condition instanceof LtExpr)
                writeOperatorAt(insn, "<");

            if (condition.getOp1() instanceof Local) {
                Local op1 = (Local) condition.getOp1();
                writeIfVar(insn, L_OP, _rep.local(m, op1));
            } else if (condition.getOp1() instanceof NumericConstant) {
                NumericConstant op1 = (NumericConstant) condition.getOp1();
                writeIfConstant(insn, L_OP, op1.toString());
            }

            if (condition.getOp2() instanceof Local) {
                Local op2 = (Local) condition.getOp2();
                writeIfVar(insn, R_OP, _rep.local(m, op2));
            } else if (condition.getOp2() instanceof NumericConstant) {
                NumericConstant op2 = (NumericConstant)condition.getOp2();
                writeIfConstant(insn, R_OP, op2.toString());
            }
        }
    }

    void writeTableSwitch(SootMethod inMethod, TableSwitchStmt stmt, Session session) {
        Value v = writeImmediate(inMethod, stmt, stmt.getKey(), null, session);
        if(!(v instanceof Local))
            throw new RuntimeException("Unexpected key for TableSwitch statement " + v + " " + v.getClass());

        Local l = (Local) v;
        InstrInfo ii = getInstrInfo(inMethod, stmt, session);
        String insn = ii.insn;
        _db.add(TABLE_SWITCH, insn, str(ii.index), _rep.local(inMethod, l), ii.methodId);

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

        Value v = writeImmediate(inMethod, stmt, stmt.getKey(), null, session);

        if(!(v instanceof Local))
            throw new RuntimeException("Unexpected key for TableSwitch statement " + v + " " + v.getClass());

        Local l = (Local) v;
        String insn = _rep.instruction(inMethod, stmt, stmtIndex);
        String methodId = methodSig(inMethod, null);

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
        String methodId = methodSig(m, null);
        _db.add(UNSUPPORTED_INSTRUCTION, insn, str(index), methodId);
    }

    /**
     * Throw statement
     */
    void writeThrow(SootMethod m, Unit unit, Local l, Session session) {
        int index = session.calcUnitNumber(unit);
        String insn = _rep.throwLocal(m, l, session);
        String methodId = methodSig(m, null);
        _db.add(THROW, insn, str(index), _rep.local(m, l), methodId);
    }

    /**
     * Throw null
     */
    void writeThrowNull(SootMethod m, Stmt stmt, Session session) {
        InstrInfo ii = calcInstrInfo(m, stmt, session);
        _db.add(THROW_NULL, ii.insn, str(ii.index), ii.methodId);
    }

    void writeExceptionHandlerPrevious(SootMethod m, Trap current, Trap previous, SessionCounter counter) {
        writeExceptionHandlerPrevious(_rep.handler(m, current, counter), _rep.handler(m, previous, counter));
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
        writeExceptionHandler(insn, _rep.signature(m), handlerIndex, exc.getName(), beginIndex, endIndex);
        writeExceptionHandlerFormal(insn, _rep.local(m, caught));
    }

    void writeThisVar(SootMethod m) {
        String methodId = methodSig(m, null);
        String thisVar = _rep.thisVar(m);
        String type = writeType(m.getDeclaringClass());
        writeThisVar(methodId, thisVar, type);
    }

    void writeMethodDeclaresException(SootMethod m, SootClass exception) {
        writeMethodDeclaresException(methodSig(m, null), writeType(exception));
    }

    void writeFormalParam(SootMethod m, int i) {
        String methodId = methodSig(m, null);
        String var = _rep.param(m, i);
        String type = writeType(m.getParameterType(i));
        writeFormalParam(methodId, var, type, i);
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

        writeLocal(local, writeType(type), methodSig(m, null));
        _db.add(VAR_SIMPLENAME, local, l.getName());
    }

    private Local freshLocal(SootMethod inMethod, String basename, Type type, Session session) {
        String varname = basename + session.nextNumber(basename);
        Local l = new JimpleLocal(varname, type);
        writeLocal(inMethod, l);
        return l;
    }

    Local writeStringConstantExpression(SootMethod inMethod, Stmt stmt, StringConstant constant, Session session) {
        // introduce a new temporary variable
        Local l = freshLocal(inMethod, "$stringconstant", RefType.v("java.lang.String"), session);
        writeAssignStringConstant(inMethod, stmt, l, constant, session);
        return l;
    }

    Local writeNullExpression(SootMethod inMethod, Stmt stmt, Type type, Session session) {
        // introduce a new temporary variable
        Local l = freshLocal(inMethod, "$null", type, session);
        writeAssignNull(inMethod, stmt, l, session);
        return l;
    }

    Local writeNumConstantExpression(SootMethod inMethod, Stmt stmt, NumericConstant constant,
                                     Type explicitType, Session session) {
        Type constantType = (explicitType == null) ? constant.getType() : explicitType;
        // introduce a new temporary variable
        Local l = freshLocal(inMethod, "$numconstant", constantType, session);
        writeAssignNumConstant(inMethod, stmt, l, constant, session);
        return l;
    }

    Local writeClassConstantExpression(SootMethod inMethod, Stmt stmt, ClassConstant constant, Session session) {
        ClassConstantInfo info = new ClassConstantInfo(constant);
        // introduce a new temporary variable
        Local l = info.isMethodType ?
            freshLocal(inMethod, "$methodtypeconstant", RefType.v("java.lang.invoke.MethodType"), session) :
            freshLocal(inMethod, "$classconstant", RefType.v("java.lang.Class"), session);
        writeAssignClassConstant(inMethod, stmt, l, info, session);
        return l;
    }

    Local writeMethodHandleConstantExpression(SootMethod inMethod, Stmt stmt, MethodHandle constant, Session session) {
        // introduce a new temporary variable
        Local l = freshLocal(inMethod, "$mhandleconstant", RefType.v("java.lang.invoke.MethodHandle"), session);
        writeAssignMethodHandleConstant(inMethod, stmt, l, constant, session);
        return l;
    }

    private Local writeMethodTypeConstantExpression(SootMethod inMethod, Stmt stmt, DoopAddons.MethodType constant, Session session) {
        // introduce a new temporary variable
        Local l = freshLocal(inMethod, "$methodtypeconstant", RefType.v("java.lang.invoke.MethodType"), session);
        writeAssignMethodTypeConstant(inMethod, stmt, l, constant, session);
        return l;
    }

    private Value writeActualParam(SootMethod inMethod, Stmt stmt, InvokeExpr expr, Session session, Value v, int idx) {
        if (v instanceof StringConstant)
            return writeStringConstantExpression(inMethod, stmt, (StringConstant) v, session);
        else if (v instanceof ClassConstant)
            return writeClassConstantExpression(inMethod, stmt, (ClassConstant) v, session);
        else if (v instanceof NumericConstant)
            return writeNumConstantExpression(inMethod, stmt, (NumericConstant) v, null, session);
        else if (v instanceof MethodHandle)
            return writeMethodHandleConstantExpression(inMethod, stmt, (MethodHandle) v, session);
        else if (v instanceof NullConstant) {
            // Giving the type of the formal argument to be used in the creation of
            // temporary var for the actual argument (whose value is null).
            Type argType = expr.getMethodRef().getParameterType(idx);
            return writeNullExpression(inMethod, stmt, argType, session);
        } else if (v instanceof Constant) {
            DoopAddons.MethodType mt = DoopAddons.methodType(v);
            if (mt != null)
                return writeMethodTypeConstantExpression(inMethod, stmt, mt, session);
            else
                throw new RuntimeException("Value has unknown constant type: " + v);
        } else if (!(v instanceof JimpleLocal))
            System.err.println("WARNING: value has unknown non-constant type: " + v.getClass().getName());
        return v;
    }

    private void writeActualParams(SootMethod inMethod, Stmt stmt, InvokeExpr expr, String invokeExprRepr, Session session) {
        boolean isInvokedynamic = (expr instanceof DynamicInvokeExpr);
        int count = expr.getArgCount();
        for (int i = 0; i < count; i++) {
            // Undo Soot's reverse order of invokedynamic arguments.
            Value arg = isInvokedynamic ? expr.getArg(count-i-1) : expr.getArg(i);
            Value v = writeActualParam(inMethod, stmt, expr, session, arg, i);
            if (v instanceof Local)
                writeActualParam(i, invokeExprRepr, _rep.local(inMethod, (Local)v));
            else
                throw new RuntimeException("Actual parameter is not a local: " + v + " " + v.getClass());
        }
        if (isInvokedynamic) {
            DynamicInvokeExpr di = (DynamicInvokeExpr)expr;
            for (int j = 0; j < di.getBootstrapArgCount(); j++) {
                Value v = di.getBootstrapArg(j);
                if (v instanceof Constant) {
                    Value vConst = writeActualParam(inMethod, stmt, expr, session, v, j);
                    if (vConst instanceof Local) {
                        Local l = (Local) vConst;
                        _db.add(BOOTSTRAP_PARAMETER, str(j), invokeExprRepr, _rep.local(inMethod, l));
                    } else
                        throw new RuntimeException("Unknown actual parameter: " + v + " of type " + v.getClass().getName());
                } else
                    throw new RuntimeException("Found non-constant argument to bootstrap method: " + di);
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
        String inMethodId = methodSig(inMethod, null);

        writeActualParams(inMethod, stmt, expr, insn, session);

        SootMethodRef exprMethodRef = expr.getMethodRef();
        String simpleName = Representation.simpleName(exprMethodRef);
        String declClass = exprMethodRef.getDeclaringClass().getName();

        LineNumberTag tag = (LineNumberTag) stmt.getTag("LineNumberTag");
        if (tag != null)
            _db.add(METHOD_INV_LINE, insn, str(tag.getLineNumber()));

        if (expr instanceof DynamicInvokeExpr) {
            writeDynamicInvoke((DynamicInvokeExpr)expr, index, insn, inMethodId);
        } else {
            String methodSig = invokeMethodSig(insn, declClass, simpleName, exprMethodRef, expr);
            if (expr instanceof StaticInvokeExpr)
                _db.add(STATIC_METHOD_INV, insn, str(index), methodSig, inMethodId);
            else if (expr instanceof VirtualInvokeExpr || expr instanceof InterfaceInvokeExpr)
                _db.add(VIRTUAL_METHOD_INV, insn, str(index), methodSig, _rep.local(inMethod, (Local) ((InstanceInvokeExpr) expr).getBase()), inMethodId);
            else if (expr instanceof SpecialInvokeExpr)
                _db.add(SPECIAL_METHOD_INV, insn, str(index), methodSig, _rep.local(inMethod, (Local) ((InstanceInvokeExpr) expr).getBase()), inMethodId);
            else
                throw new RuntimeException("Cannot handle invoke expr: " + expr);
        }

        return insn;
    }

    // Special handling for polymorphic-signature methods.
    private String invokeMethodSig(String insn, String declClass, String simpleName, SootMethodRef exprMethodRef, InvokeExpr expr) {
        if (!simpleName.equals("<init>") && DoopAddons.polymorphicHandling(declClass, simpleName)) {
            _db.add(POLYMORPHIC_INVOCATION, insn, simpleName);
            return Representation.signature(exprMethodRef);
        } else
            return _rep.signature(expr.getMethod());
    }

    private String getBootstrapSig(DynamicInvokeExpr di) {
        SootMethodRef bootstrapMeth = di.getBootstrapMethodRef();
        if (bootstrapMeth.getDeclaringClass().isPhantom()) {
            String bootstrapSig = Representation.signature(bootstrapMeth);
            phantoms.reportPhantom("Bootstrap method", bootstrapSig);
            _db.add(PHANTOM_METHOD, bootstrapSig);
            return bootstrapSig;
        } else
            return _rep.signature(bootstrapMeth.resolve());
    }

    private void writeDynamicInvoke(DynamicInvokeExpr di, int index, String insn, String methodId) {
        SootMethodRef dynInfo = di.getMethodRef();
        SigInfo dynSig = new SigInfo(dynInfo, true);
        for (int pIdx = 0; pIdx < dynSig.arity; pIdx++)
            writeInvokedynamicParameterType(insn, pIdx, dynInfo.getParameterType(pIdx).toString());
        writeInvokedynamic(insn, index, getBootstrapSig(di), dynInfo.getName(), dynSig.retType, dynSig.arity, dynSig.paramTypes, di.getHandleTag(), methodId);
    }

    private Value writeImmediate(SootMethod inMethod, Stmt stmt, Value v, Type vType, Session session) {
        if (v instanceof Constant) {
            if (v instanceof StringConstant)
                v = writeStringConstantExpression(inMethod, stmt, (StringConstant) v, session);
            else if (v instanceof ClassConstant)
                v = writeClassConstantExpression(inMethod, stmt, (ClassConstant) v, session);
            else if (v instanceof NumericConstant)
                v = writeNumConstantExpression(inMethod, stmt, (NumericConstant) v, vType, session);
            else
                System.err.println("ERROR: unknown type of immediate: " + v.getClass());
        }
        return v;
    }

    void writeAssignBinop(SootMethod m, AssignStmt stmt, Local left, BinopExpr right, Session session) {
        InstrInfo ii = calcInstrInfo(m, stmt, session);
        String insn = ii.insn;
        writeAssignBinop(insn, ii.index, _rep.local(m, left), ii.methodId);

        if (right instanceof AddExpr)
                writeOperatorAt(insn, "+");
        else if (right instanceof SubExpr)
                writeOperatorAt(insn, "-");
        else if (right instanceof MulExpr)
                writeOperatorAt(insn, "*");
        else if (right instanceof DivExpr)
                writeOperatorAt(insn, "/");
        else if (right instanceof RemExpr)
                writeOperatorAt(insn, "%");
        else if (right instanceof AndExpr)
                writeOperatorAt(insn, "&");
        else if (right instanceof OrExpr)
                writeOperatorAt(insn, "|");
        else if (right instanceof XorExpr)
                writeOperatorAt(insn, "^");
        else if (right instanceof ShlExpr)
                writeOperatorAt(insn, "<<");
        else if (right instanceof ShrExpr)
                writeOperatorAt(insn, ">>");
        else if (right instanceof UshrExpr)
                writeOperatorAt(insn, ">>>");
        else if (right instanceof CmpExpr)
                writeOperatorAt(insn, "cmp");
        else if (right instanceof CmplExpr)
                writeOperatorAt(insn, "cmpl");
        else if (right instanceof CmpgExpr)
                writeOperatorAt(insn, "cmpg");
        else
                writeOperatorAt(insn, "??");

        if (right.getOp1() instanceof Local) {
            Local op1 = (Local) right.getOp1();
            writeAssignOperFrom(insn, L_OP, _rep.local(m, op1));
        } else if (right.getOp1() instanceof NumericConstant) {
            NumericConstant cons = (NumericConstant) right.getOp1();
            writeAssignOperFromConstant(insn, L_OP, cons.toString());
        }

        if (right.getOp2() instanceof Local) {
            Local op2 = (Local) right.getOp2();
            writeAssignOperFrom(insn, R_OP, _rep.local(m, op2));
        } else if (right.getOp2() instanceof NumericConstant) {
            NumericConstant cons = (NumericConstant) right.getOp2();
            writeAssignOperFromConstant(insn, R_OP, cons.toString());
        }
    }

    void writeAssignUnop(SootMethod m, AssignStmt stmt, Local left, UnopExpr right, Session session) {
        InstrInfo ii = calcInstrInfo(m, stmt, session);
        String insn = ii.insn;
        writeAssignUnop(insn, ii.index, _rep.local(m, left), ii.methodId);

        if (right instanceof LengthExpr)
                writeOperatorAt(insn, "len");
        else if (right instanceof NegExpr)
                writeOperatorAt(insn, "~");
        else
                writeOperatorAt(insn, "??");

        if (right.getOp() instanceof Local) {
            Local op = (Local) right.getOp();
            writeAssignOperFrom(insn, L_OP, _rep.local(m, op));
        } else if (right.getOp() instanceof NumericConstant) {
            NumericConstant cons = (NumericConstant) right.getOp();
            writeAssignOperFromConstant(insn, L_OP, cons.toString());
        }
    }

    void writeAssignInstanceOf(SootMethod m, AssignStmt stmt, Local to, Local from, Type t, Session session) {
        InstrInfo ii = calcInstrInfo(m, stmt, session);
        _db.add(ASSIGN_INSTANCE_OF, ii.insn, str(ii.index), _rep.local(m, from), _rep.local(m, to), writeType(t), ii.methodId);
    }

    void writeAssignPhantomInvoke(SootMethod m, AssignStmt stmt, Session session) {
        InstrInfo ii = calcInstrInfo(m, stmt, session);
        _db.add(ASSIGN_PHANTOM_INVOKE, ii.insn, str(ii.index), ii.methodId);
    }

    void writeBreakpointStmt(SootMethod m, Stmt stmt, Session session) {
        InstrInfo ii = calcInstrInfo(m, stmt, session);
        _db.add(BREAKPOINT_STMT, ii.insn, str(ii.index), ii.methodId);
    }

    void writeFieldInitialValue(SootField f) {
        String fieldId = _rep.signature(f);
        List<Tag> tagList = f.getTags();
        for (Tag tag : tagList)
            if (tag instanceof ConstantValueTag) {
                String val = ((ConstantValueTag)tag).getConstant().toString();
                _db.add(FIELD_INITIAL_VALUE, fieldId, val);
                // Put constant in appropriate "raw" input facts.
                if ((tag instanceof IntegerConstantValueTag) ||
                    (tag instanceof DoubleConstantValueTag) ||
                    (tag instanceof LongConstantValueTag) ||
                    (tag instanceof FloatConstantValueTag)) {
                    // Trim last non-digit qualifier (e.g. 'L' in long constants).
                    int len = val.length();
                    if (!Character.isDigit(val.charAt(len-1)))
                        val = val.substring(0, len-1);
                    writeNumConstantRawInt(val);
                } else if (tag instanceof StringConstantValueTag) {
                    writeStringConstant(val);
                } else
                    System.err.println("Unsupported field tag " + tag.getClass());
            }
    }

    public void writePreliminaryFacts(Collection<SootClass> classes,
                                      BasicJavaSupport java, boolean debug) {
        classes.stream().filter(SootClass::isApplicationClass).forEachOrdered(this::writeApplicationClass);
        writePreliminaryFacts(java, debug);
    }

    boolean checkAndRegisterPhantom(Object phantom) {
        if (seenPhantoms.contains(phantom))
            return true;

        seenPhantoms.add(phantom);
        return false;
    }

    @Override
    public void writeLastFacts(BasicJavaSupport java) {
        super.writeLastFacts(java);
        phantoms.showPhantomInfo();
    }

    static class SigInfo {
        final int arity;
        final String retType;
        final String paramTypes;
        SigInfo(SootMethodRef ref, boolean reverse) {
            List<Type> paramTypes = ref.getParameterTypes();
            if (reverse)
                paramTypes = Lists.reverse(paramTypes);

            this.arity = paramTypes.size();
            this.retType = ref.getReturnType().toString();

            StringJoiner joiner = new StringJoiner(",");
            paramTypes.forEach(p -> joiner.add(p.toString()));
            this.paramTypes = joiner.toString();
        }
    }

    private InstrInfo calcInstrInfo(SootMethod m, Stmt stmt, Session session) {
        return new InstrInfo(m, stmt, session, true);
    }

    private InstrInfo getInstrInfo(SootMethod m, Stmt stmt, Session session) {
        return new InstrInfo(m, stmt, session, false);
    }

    class InstrInfo {
        final int index;
        final String insn;
        final String methodId;
        InstrInfo(SootMethod m, Stmt stmt, Session session, boolean calc) {
            if (calc)
                this.index = session.calcUnitNumber(stmt);
            else
                this.index = session.getUnitNumber(stmt);
            this.insn = _rep.instruction(m, stmt, index);
            this.methodId = methodSig(m, null);
        }
    }
}
