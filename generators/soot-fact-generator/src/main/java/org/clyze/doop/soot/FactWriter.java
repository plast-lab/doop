package org.clyze.doop.soot;

import com.google.common.collect.Lists;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.clyze.doop.common.*;
import org.clyze.utils.TypeUtils;
import soot.*;
import soot.jimple.*;
import soot.jimple.internal.JimpleLocal;
import soot.jimple.toolkits.typing.fast.BottomType;
import soot.shimple.PhiExpr;
import soot.tagkit.*;
import soot.util.backend.ASMBackendUtils;

import static org.clyze.doop.common.JavaRepresentation.*;
import static org.clyze.doop.common.PredicateFile.*;

/**
 * FactWriter determines the format of a fact and adds it to a
 * database.
 */
class FactWriter extends JavaFactWriter {
    final Representation _rep;
    private final Map<String, Type> _varTypeMap = new ConcurrentHashMap<>();
    private final Phantoms phantoms;
    private final Collection<Object> seenPhantoms = ConcurrentHashMap.newKeySet();
    private final Map<Unit, Collection<InstrInfo>> expandedPhiNodes = new ConcurrentHashMap<>();

    FactWriter(Database db, SootParameters params, Representation rep, Phantoms phantoms) {
        super(db, params);
        this._rep = rep;
        this.phantoms = phantoms;
    }

    public String methodSig(SootMethod m, String methodRaw) {
        if (methodRaw == null)
            methodRaw = _rep.signature(m);
        return methodRaw; // hashMethodNameIfLong(methodRaw);
    }

    private static String getAnnotationType(AnnotationTag aTag) {
        return TypeUtils.raiseTypeId(aTag.getType());
    }

    String writeMethod(SootMethod m) {
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
        return methodId;
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

    protected void writePhantomMethod(String sig) {
        phantoms.reportPhantom("Method", sig);
        super.writePhantomMethod(sig);
    }

    void writePhantomBasedMethod(String sig) {
        phantoms.reportPhantomSignature(sig);
        _db.add(PHANTOM_BASED_METHOD, sig);
    }

    void writeEnterMonitor(InstrInfo ii, Local var) {
        String methodId = ii.methodId;
        _db.add(ENTER_MONITOR, ii.insn, str(ii.index), _rep.local(methodId, var), methodId);
    }

    void writeExitMonitor(InstrInfo ii, Local var) {
        String methodId = ii.methodId;
        _db.add(EXIT_MONITOR, ii.insn, str(ii.index), _rep.local(methodId, var), methodId);
    }

    void writeAssignLocal(InstrInfo ii, Local to, Local from) {
        String methodId = ii.methodId;
        writeAssignLocal(ii.insn, ii.index, _rep.local(methodId, from), _rep.local(ii.methodId, to), methodId);
    }

    void writeAssignThisToLocal(InstrInfo ii, Local to) {
        String methodId = ii.methodId;
        writeAssignLocal(ii.insn, ii.index, _rep.thisVar(methodId), _rep.local(methodId, to), methodId);
    }

    void writeAssignLocal(InstrInfo ii, Local to, ParameterRef ref) {
        String methodId = ii.methodId;
        writeAssignLocal(ii.insn, ii.index, _rep.param(methodId, ref.getIndex()), _rep.local(methodId, to), methodId);
    }

    void writePhiAssign(String methodId, AssignStmt stmt, Local left, PhiExpr phiExpr, SessionCounter session) {
        Collection<InstrInfo> newAssignments = new ArrayList<>();
        for (Value alternative : (phiExpr).getValues()) {
            InstrInfo altInstrInfo = new InstrInfo(methodId, "phi-assign", session);
            writeAssignLocal(altInstrInfo, left, (Local) alternative);
            newAssignments.add(altInstrInfo);
        }
        expandedPhiNodes.put(stmt, newAssignments);
    }

    void writeWithPossiblePhiTarget(Unit target, SessionCounter session,
                                            Consumer<Integer> writerLambda) {
        Collection<InstrInfo> phiNodes = expandedPhiNodes.get(target);
        if (phiNodes == null) {
            session.calcInstructionIndex(target);
            int indexTo = session.getInstructionIndex(target);
            writerLambda.accept(indexTo);
        } else {
            Collection<Integer> targetIndices = phiNodes.stream().map(ii -> ii.index).collect(Collectors.toList());
            for (int indexTo : targetIndices)
                writerLambda.accept(indexTo);
        }
    }

    void writeAssignInvoke(SootMethod inMethod, Stmt stmt, InstrInfo ii, Local to, SessionCounter session) {
        String invokeInstructionId = writeInvoke(inMethod, stmt, ii, session);
        _db.add(ASSIGN_RETURN_VALUE, invokeInstructionId, _rep.local(ii.methodId, to));
    }

    private void writeArraySize(InstrInfo ii, Value sizeVal, int pos, String heap) {
        if (sizeVal instanceof IntConstant) {
            IntConstant size = (IntConstant) sizeVal;
            _db.add(ARRAY_ALLOC_CONST_SIZE, ii.insn, str(pos), str(size.value));
            if(size.value == 0) _db.add(EMPTY_ARRAY, heap);
        }
        else if (sizeVal instanceof Local)
            _db.add(ARRAY_ALLOC, ii.insn, str(pos), _rep.local(ii.methodId, (Local)sizeVal));
    }

    void writeAssignHeapAllocation(Stmt stmt, InstrInfo ii, Local l, Value expr, SessionCounter session) {
        String methodId = ii.methodId;
        String heap = _rep.heapAlloc(methodId, expr, session);

        _db.add(NORMAL_HEAP, heap, writeType(expr.getType()));
        _db.add(ASSIGN_HEAP_ALLOC, ii.insn, str(ii.index), heap, _rep.local(methodId, l), methodId, ""+getLineNumberFromStmt(stmt));

        if (expr instanceof NewArrayExpr) {
            NewArrayExpr newArray = (NewArrayExpr) expr;
            writeArraySize(ii, newArray.getSize(), 0, heap);
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
    void writeAssignNewMultiArrayExpr(Stmt stmt, InstrInfo ii, Local l, NewMultiArrayExpr expr, SessionCounter session) {
        writeAssignNewMultiArrayExprHelper(stmt, ii, l, _rep.local(ii.methodId, l), expr, (ArrayType) expr.getType(), session, 0);
    }

    private void writeAssignNewMultiArrayExprHelper(Stmt stmt, InstrInfo ii, Local l, String assignTo, NewMultiArrayExpr expr, ArrayType arrayType, SessionCounter session, int pos) {
        String methodId = ii.methodId;
        String heap = _rep.heapMultiArrayAlloc(methodId, /* expr, */ arrayType, session);

        _db.add(NORMAL_HEAP, heap, writeType(arrayType));
        _db.add(ASSIGN_HEAP_ALLOC, ii.insn, str(ii.index), heap, assignTo, methodId, ""+getLineNumberFromStmt(stmt));
        if (pos < expr.getSizeCount())
            writeArraySize(ii, expr.getSize(pos), pos, heap);

        Type componentType = getComponentType(arrayType);
        if (componentType instanceof ArrayType) {
            String childAssignTo = _rep.newLocalIntermediate(methodId, l, session);
            writeAssignNewMultiArrayExprHelper(stmt, new InstrInfo(ii.methodId, "assign-new-multi-array", session), l, childAssignTo, expr, (ArrayType) componentType, session, pos+1);

            String storeInsn = ii.insn;
            _db.add(STORE_ARRAY_INDEX, storeInsn, str(ii.index), childAssignTo, assignTo, methodId);
            writeLocal(childAssignTo, writeType(componentType), methodId);
            _db.add(ARRAY_INSN_INDEX, storeInsn, childAssignTo);
        }
    }

    // The commented-out code below is what used to be in Doop2. It is not
    // equivalent to code in old Doop. I (YS) tried to have a more compatible
    // approach for comparison purposes.
    /*
    public void writeAssignNewMultiArrayExpr(SootMethod m, Stmt stmt, Local l, NewMultiArrayExpr expr, SessionCounter session) {
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

        int index = session.calcInstructionIndex(stmt);
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

    void writeAssignStringConstant(Stmt stmt, InstrInfo ii, Local l, StringConstant s) {
        String constant = s.toString();
        String content = constant.substring(1, constant.length() - 1);
        String heapId = writeStringConstant(content);
        String methodId = ii.methodId;
        _db.add(ASSIGN_HEAP_ALLOC, ii.insn, str(ii.index), heapId, _rep.local(methodId, l), methodId, ""+getLineNumberFromStmt(stmt));
    }

    void writeAssignNull(InstrInfo ii, Local l) {
        String methodId = ii.methodId;
        _db.add(ASSIGN_NULL, ii.insn, str(ii.index), _rep.local(methodId, l), methodId);
    }

    void writeAssignNumConstant(InstrInfo ii, Local l, NumericConstant constant) {
        String methodId = ii.methodId;
        _db.add(ASSIGN_NUM_CONST, ii.insn, str(ii.index), constant.toString(), _rep.local(methodId, l), methodId);
    }

    private void writeAssignMethodHandleConstant(InstrInfo ii, Local l, MethodHandle constant) {
        String handleMethod = Representation.signature(constant.getMethodRef());
        String heap = methodHandleConstant(handleMethod);
        SigInfo si = new SigInfo(constant.getMethodRef(), false);

        writeMethodHandleConstant(heap, handleMethod, si.retType, si.paramTypes, si.arity);
        String methodId = ii.methodId;
        _db.add(ASSIGN_HEAP_ALLOC, ii.insn, str(ii.index), heap, _rep.local(methodId, l), methodId, "0");
    }

    private void writeAssignMethodTypeConstant(InstrInfo ii, Local l, DoopAddons.MethodType constant) {
        String retType = constant.getReturnType();
        List<String> paramTypesList = constant.getParameterTypes();
        int arity = paramTypesList.size();
        String[] paramTypes = new String[arity];
        paramTypes = paramTypesList.toArray(paramTypes);
        writeMethodTypeConstant(retType, paramTypes, null);
        String params = concatenate(paramTypes);
        String mt = "<method type (" + params + ")" + retType + ">";
        String methodId = ii.methodId;
        _db.add(ASSIGN_HEAP_ALLOC, ii.insn, str(ii.index), mt, _rep.local(methodId, l), methodId, "0");
    }

    void writeAssignClassConstant(InstrInfo ii, Local l, ClassConstant constant) {
        writeAssignClassConstant(ii, l, new ClassConstantInfo(constant));
    }

    private void writeAssignClassConstant(InstrInfo ii, Local l, ClassConstantInfo info) {
        if (info.isMethodType)
            writeMethodTypeConstant(info.heap);
        else
            writeClassHeap(info.heap, info.actualType);

        // REVIEW: the class object is not explicitly written. Is this always ok?
        String methodId = ii.methodId;
        _db.add(ASSIGN_HEAP_ALLOC, ii.insn, str(ii.index), info.heap, _rep.local(methodId, l), methodId, "0");
    }

    void writeAssignCast(InstrInfo ii, Local to, Local from, Type t) {
        String methodId = ii.methodId;
        _db.add(ASSIGN_CAST, ii.insn, str(ii.index), _rep.local(methodId, from), _rep.local(methodId, to), writeType(t), methodId);
    }

    void writeAssignCastNumericConstant(InstrInfo ii, Local to, NumericConstant constant, Type t) {
        String methodId = ii.methodId;
        String val = constant.toString();
        if (constant instanceof ArithmeticConstant) {
            if (constant instanceof LongConstant)
                writeNumConstantRaw(val, "long");
            else if (constant instanceof IntConstant)
                writeNumConstantRaw(val, "int");
            else
                System.err.println("WARNING: arithmetic constant is not long/int: " + constant);
        }
        _db.add(ASSIGN_CAST_NUM_CONST, ii.insn, str(ii.index), val, _rep.local(methodId, to), writeType(t), methodId);
    }

    void writeAssignCastNull(InstrInfo ii, Local to, Type t) {
        String methodId = ii.methodId;
        _db.add(ASSIGN_CAST_NULL, ii.insn, str(ii.index), _rep.local(methodId, to), writeType(t), methodId);
    }

    void writeStoreInstanceField(InstrInfo ii, SootField f, Local base, Local from) {
        writeInstanceField(ii, f, base, from, STORE_INST_FIELD);
    }

    void writeLoadInstanceField(InstrInfo ii, SootField f, Local base, Local to) {
        writeInstanceField(ii, f, base, to, LOAD_INST_FIELD);
    }

    private void writeInstanceField(InstrInfo ii, SootField f, Local base, Local var, PredicateFile storeOrLoadInstField) {
        String methodId = ii.methodId;
        String fieldId = writeField(f);
        if (fieldId != null)
            _db.add(storeOrLoadInstField, ii.insn, str(ii.index), _rep.local(methodId, var), _rep.local(methodId, base), fieldId, methodId);
    }

    void writeStoreStaticField(InstrInfo ii, SootField f, Local from) {
        writeStaticField(ii, f, from, STORE_STATIC_FIELD);
    }

    void writeLoadStaticField(InstrInfo ii, SootField f, Local to) {
        writeStaticField(ii, f, to, LOAD_STATIC_FIELD);
    }

    private void writeStaticField(InstrInfo ii, SootField f, Local var, PredicateFile staticFieldFacts) {
        String methodId = ii.methodId;
        String fieldId = writeField(f);
        if (fieldId != null)
            _db.add(staticFieldFacts, ii.insn, str(ii.index), _rep.local(methodId, var), fieldId, methodId);
    }

    void writeLoadArrayIndex(Stmt stmt, InstrInfo ii, Local base, Local to, Value arrIndex) {
        writeLoadOrStoreArrayIndex(stmt, ii, base, to, arrIndex, LOAD_ARRAY_INDEX);
    }

    void writeStoreArrayIndex(Stmt stmt, InstrInfo ii, Local base, Local from, Value arrIndex) {
        writeLoadOrStoreArrayIndex(stmt, ii, base, from, arrIndex, STORE_ARRAY_INDEX);
    }

    private void writeLoadOrStoreArrayIndex(Stmt stmt, InstrInfo ii, Local base, Local var, Value arrIndex, PredicateFile predicateFile) {
        String methodId = ii.methodId;
        String insn = ii.insn;
        _db.add(predicateFile, insn, str(ii.index), _rep.local(methodId, var), _rep.local(methodId, base), methodId);

        if (arrIndex instanceof Local)
            _db.add(ARRAY_INSN_INDEX, insn, _rep.local(methodId, (Local) arrIndex));
        else if (arrIndex instanceof IntConstant)
            _db.add(ARRAY_NUM_INDEX, insn, str(((IntConstant) arrIndex).value));
        else
            throw new RuntimeException("Cannot handle assignment: " + stmt + " (index: " + arrIndex.getClass() + ")");
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

    void writeMethodModifier(String methodId, String modifier) {
        _db.add(METHOD_MODIFIER, modifier, methodId);
    }

    void writeReturn(InstrInfo ii, Local l) {
        String methodId = ii.methodId;
        _db.add(RETURN, ii.insn, str(ii.index), _rep.local(methodId, l), methodId);
    }

    void writeReturnVoid(InstrInfo ii) {
        _db.add(RETURN_VOID, ii.insn, str(ii.index), ii.methodId);
    }

    // The return var of native methods is exceptional, in that it does not
    // correspond to a return instruction.
    void writeNativeReturnVar(String methodId, Type returnType) {
        if (!(returnType instanceof VoidType)) {
            String var = _rep.nativeReturnVar(methodId);
            _db.add(NATIVE_RETURN_VAR, var, methodId);
            writeLocal(var, writeType(returnType), methodId);
        }
    }

    void writeGoto(GotoStmt stmt, InstrInfo ii, SessionCounter session) {
        session.calcInstructionIndex(stmt);
        writeWithPossiblePhiTarget(stmt.getTarget(), session, (indexTo -> _db.add(GOTO, ii.insn, str(ii.index), str(indexTo), ii.methodId)));
    }

    /**
     * If
     */
    void writeIf(IfStmt stmt, InstrInfo ii, int indexTo) {
        // index was already computed earlier
        int index = ii.index;
        String insn = ii.insn;

        String methodId = ii.methodId;
        writeIf(insn, index, indexTo, methodId);

        Value condStmt = stmt.getCondition();
        if (condStmt instanceof ConditionExpr) {
            ConditionExpr condition = (ConditionExpr) condStmt;

            Local dummy = new JimpleLocal("tmp" + insn, BooleanType.v());
            writeDummyIfVar(insn, _rep.local(methodId, dummy));

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
                writeIfVar(insn, L_OP, _rep.local(methodId, op1));
            } else if (condition.getOp1() instanceof NumericConstant) {
                NumericConstant op1 = (NumericConstant) condition.getOp1();
                writeIfConstant(insn, L_OP, op1.toString());
            }

            if (condition.getOp2() instanceof Local) {
                Local op2 = (Local) condition.getOp2();
                writeIfVar(insn, R_OP, _rep.local(methodId, op2));
            } else if (condition.getOp2() instanceof NumericConstant) {
                NumericConstant op2 = (NumericConstant)condition.getOp2();
                writeIfConstant(insn, R_OP, op2.toString());
            }
        }
    }

    void writeTableSwitch(TableSwitchStmt stmt, InstrInfo ii, SessionCounter session) {
        String methodId = ii.methodId;
        Value v = writeImmediate(stmt, methodId, stmt.getKey(), null, session);
        if(!(v instanceof Local))
            throw new RuntimeException("Unexpected key for TableSwitch statement " + v + " " + v.getClass());

        Local l = (Local) v;
        String insn = ii.insn;
        _db.add(TABLE_SWITCH, insn, str(ii.index), _rep.local(methodId, l), methodId);

        writeTableSwitchTarget(stmt, session, insn);

        session.calcInstructionIndex(stmt.getDefaultTarget());
        int defaultIndex = session.getInstructionIndex(stmt.getDefaultTarget());
        _db.add(TABLE_SWITCH_DEFAULT, insn, str(defaultIndex));
    }

    private void writeTableSwitchTarget(TableSwitchStmt stmt, SessionCounter session, String insn) {
        for (int tgIndex = stmt.getLowIndex(), i = 0; tgIndex <= stmt.getHighIndex(); tgIndex++, i++) {
            String tgIndexStr = str(tgIndex);
            writeWithPossiblePhiTarget(stmt.getTarget(i), session, (indexTo -> _db.add(TABLE_SWITCH_TARGET, insn, tgIndexStr, str(indexTo))));
        }
    }

    void writeLookupSwitch(LookupSwitchStmt stmt, InstrInfo ii, SessionCounter session) {
        String methodId = ii.methodId;
        int stmtIndex = session.getInstructionIndex(stmt);

        Value v = writeImmediate(stmt, methodId, stmt.getKey(), null, session);

        if(!(v instanceof Local))
            throw new RuntimeException("Unexpected key for TableSwitch statement " + v + " " + v.getClass());

        Local l = (Local) v;
        String insn = ii.insn;

        _db.add(LOOKUP_SWITCH, insn, str(stmtIndex), _rep.local(methodId, l), methodId);

        writeLookupSwitchTarget(stmt, session, insn);

        session.calcInstructionIndex(stmt.getDefaultTarget());
        int defaultIndex = session.getInstructionIndex(stmt.getDefaultTarget());

        _db.add(LOOKUP_SWITCH_DEFAULT, insn, str(defaultIndex));
    }

    private void writeLookupSwitchTarget(LookupSwitchStmt stmt, SessionCounter session, String insn) {
        for (int i = 0, end = stmt.getTargetCount(); i < end; i++) {
            int tgIndex = stmt.getLookupValue(i);
            writeWithPossiblePhiTarget(stmt.getTarget(i), session, (indexTo -> _db.add(LOOKUP_SWITCH_TARGET, insn, str(tgIndex), str(indexTo))));
        }
    }

    void writeUnsupported(Unit unit, InstrInfo ii, SessionCounter session) {
        int index = session.calcInstructionIndex(unit);
        _db.add(UNSUPPORTED_INSTRUCTION, ii.insn, str(index), ii.methodId);
    }

    /**
     * Throw statement
     */
    void writeThrow(String methodId, Unit unit, Local l, SessionCounter session) {
        int index = session.calcInstructionIndex(unit);
        String insn = _rep.throwLocal(methodId, l, session);
        _db.add(THROW, insn, str(index), _rep.local(methodId, l), methodId);
    }

    /**
     * Throw null
     */
    void writeThrowNull(InstrInfo ii) {
        _db.add(THROW_NULL, ii.insn, str(ii.index), ii.methodId);
    }

    void writeExceptionHandlerPrevious(String methodId, Trap current, Trap previous, SessionCounter counter) {
        writeExceptionHandlerPrevious(_rep.handler(methodId, current, counter), _rep.handler(methodId, previous, counter));
    }

    void writeExceptionHandler(String methodId, Trap handler, SessionCounter session) {
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

        String insn = _rep.handler(methodId, handler, session);
        int handlerIndex = session.getInstructionIndex(handler.getHandlerUnit());
        session.calcInstructionIndex(handler.getBeginUnit());
        int beginIndex = session.getInstructionIndex(handler.getBeginUnit());
        session.calcInstructionIndex(handler.getEndUnit());
        int endIndex = session.getInstructionIndex(handler.getEndUnit());
        writeExceptionHandler(insn, methodId, handlerIndex, exc.getName(), beginIndex, endIndex);
        writeExceptionHandlerFormal(insn, _rep.local(methodId, caught));
    }

    void writeThisVar(String methodId, SootClass declaringClass) {
        String thisVar = _rep.thisVar(methodId);
        String type = writeType(declaringClass);
        writeThisVar(methodId, thisVar, type);
    }

    void writeMethodDeclaresException(SootMethod m, SootClass exception) {
        writeMethodDeclaresException(methodSig(m, null), writeType(exception));
    }

    void writeFormalParam(String methodId, Type type, int i) {
        String var = _rep.param(methodId, i);
        writeFormalParam(methodId, var, writeType(type), i);
    }

    void writeLocal(String methodId, Local l) {
        String local = _rep.local(methodId, l);
        Type type;

        if (_varTypeMap.containsKey(local))
            type = _varTypeMap.get(local);
        else {
            type = l.getType();
            _varTypeMap.put(local, type);
        }

        writeLocal(local, writeType(type), methodId);
        _db.add(VAR_SIMPLENAME, local, l.getName());
    }

    private Local freshLocal(String inMethod, String basename, Type type, SessionCounter session) {
        String varname = basename + session.nextNumber(basename);
        Local l = new JimpleLocal(varname, type);
        writeLocal(inMethod, l);
        return l;
    }

    private FreshAssignLocal newAssignForFreshLocal(String inMethod, String basename, Type type, SessionCounter session) {
        return newInstructionWithFreshLocal(inMethod, "fresh-null-assign", basename, type, session);
    }

    private FreshAssignLocal newInstructionWithFreshLocal(String inMethod, String kind, String basename, Type type, SessionCounter session) {
        return new FreshAssignLocal(freshLocal(inMethod, basename, type, session),
                new InstrInfo(inMethod, kind, session));
    }

    static class FreshAssignLocal {
        final Local local;
        final InstrInfo ii;

        FreshAssignLocal(Local local, InstrInfo ii) {
            this.local = local;
            this.ii = ii;
        }
    }

    Local writeStringConstantExpression(Stmt stmt, String methodId, StringConstant constant, SessionCounter session) {
        // introduce a new temporary variable
        FreshAssignLocal fal = newAssignForFreshLocal(methodId, "$stringconstant", RefType.v("java.lang.String"), session);
        Local l = fal.local;
        writeAssignStringConstant(stmt, fal.ii, l, constant);
        return l;
    }

    Local writeNullExpression(String methodId, Type type, SessionCounter session) {
        // introduce a new temporary variable
        FreshAssignLocal fal = newAssignForFreshLocal(methodId, "$null", type, session);
        Local l = fal.local;
        writeAssignNull(fal.ii, l);
        return l;
    }

    Local writeNumConstantExpression(String methodId, NumericConstant constant,
                                     Type explicitType, SessionCounter session) {
        Type constantType = (explicitType == null) ? constant.getType() : explicitType;
        // introduce a new temporary variable
        FreshAssignLocal fal = newAssignForFreshLocal(methodId, "$numconstant", constantType, session);
        Local l = fal.local;
        writeAssignNumConstant(fal.ii, l, constant);
        return l;
    }

    Local writeClassConstantExpression(String methodId, ClassConstant constant, SessionCounter session) {
        ClassConstantInfo info = new ClassConstantInfo(constant);
        // introduce a new temporary variable
        FreshAssignLocal fal = info.isMethodType ?
            newAssignForFreshLocal(methodId, "$methodtypeconstant", RefType.v("java.lang.invoke.MethodType"), session) :
            newAssignForFreshLocal(methodId, "$classconstant", RefType.v("java.lang.Class"), session);
        Local l = fal.local;
        writeAssignClassConstant(fal.ii, l, info);
        return l;
    }

    Local writeMethodHandleConstantExpression(String methodId, MethodHandle constant, SessionCounter session) {
        // introduce a new temporary variable
        FreshAssignLocal fal = newAssignForFreshLocal(methodId, "$mhandleconstant", RefType.v("java.lang.invoke.MethodHandle"), session);
        Local l = fal.local;
        writeAssignMethodHandleConstant(fal.ii, l, constant);
        return l;
    }

    private Local writeMethodTypeConstantExpression(InstrInfo ii, DoopAddons.MethodType constant, SessionCounter session) {
        // introduce a new temporary variable
        FreshAssignLocal fal = newAssignForFreshLocal(ii.methodId, "$methodtypeconstant", RefType.v("java.lang.invoke.MethodType"), session);
        Local l = fal.local;
        writeAssignMethodTypeConstant(fal.ii, l, constant);
        return l;
    }

    private Value writeActualParam(Stmt stmt, InstrInfo ii, InvokeExpr expr, SessionCounter session, Value v, int idx) {
        if (v instanceof StringConstant)
            return writeStringConstantExpression(stmt, ii.methodId, (StringConstant) v, session);
        else if (v instanceof ClassConstant)
            return writeClassConstantExpression(ii.methodId, (ClassConstant) v, session);
        else if (v instanceof NumericConstant)
            return writeNumConstantExpression(ii.methodId, (NumericConstant) v, null, session);
        else if (v instanceof MethodHandle)
            return writeMethodHandleConstantExpression(ii.methodId, (MethodHandle) v, session);
        else if (v instanceof NullConstant) {
            // Giving the type of the formal argument to be used in the creation of
            // temporary var for the actual argument (whose value is null).
            Type argType = expr.getMethodRef().getParameterType(idx);
            return writeNullExpression(ii.methodId, argType, session);
        } else if (v instanceof Constant) {
            DoopAddons.MethodType mt = DoopAddons.methodType(v);
            if (mt != null)
                return writeMethodTypeConstantExpression(ii, mt, session);
            else
                throw new RuntimeException("Value has unknown constant type: " + v);
        } else if (!(v instanceof JimpleLocal))
            System.err.println("WARNING: value has unknown non-constant type: " + v.getClass().getName());
        return v;
    }

    private void writeActualParams(Stmt stmt, InstrInfo ii, InvokeExpr expr,
                                   String invokeExprRepr, SessionCounter session) {
        String methodId = ii.methodId;
        boolean isInvokedynamic = (expr instanceof DynamicInvokeExpr);
        int count = expr.getArgCount();
        for (int i = 0; i < count; i++) {
            // Undo Soot's reverse order of invokedynamic arguments.
            Value arg = isInvokedynamic ? expr.getArg(count-i-1) : expr.getArg(i);
            Value v = writeActualParam(stmt, ii, expr, session, arg, i);
            if (v instanceof Local)
                writeActualParam(i, invokeExprRepr, _rep.local(methodId, (Local)v));
            else
                throw new RuntimeException("Actual parameter is not a local: " + v + " " + v.getClass());
        }
        if (isInvokedynamic) {
            DynamicInvokeExpr di = (DynamicInvokeExpr)expr;
            for (int j = 0; j < di.getBootstrapArgCount(); j++) {
                Value v = di.getBootstrapArg(j);
                if (v instanceof Constant) {
                    Value vConst = writeActualParam(stmt, ii, expr, session, v, j);
                    if (vConst instanceof Local) {
                        Local l = (Local) vConst;
                        _db.add(BOOTSTRAP_PARAMETER, str(j), invokeExprRepr, _rep.local(methodId, l));
                    } else
                        throw new RuntimeException("Unknown actual parameter: " + v + " of type " + v.getClass().getName());
                } else
                    throw new RuntimeException("Found non-constant argument to bootstrap method: " + di);
            }
        }
    }

    String writeInvoke(SootMethod inMethod, Stmt stmt, InstrInfo ii, SessionCounter session) {
        InvokeExpr expr = stmt.getInvokeExpr();
        String insn = _rep.invoke(inMethod, expr, session);
        return writeInvokeHelper(insn, stmt, ii, expr, session);
    }

    private String writeInvokeHelper(String insn, Stmt stmt, InstrInfo ii,
                                     InvokeExpr expr, SessionCounter session) {
        writeActualParams(stmt, ii, expr, insn, session);

        SootMethodRef exprMethodRef = expr.getMethodRef();
        String simpleName = Representation.simpleName(exprMethodRef);
        String declClass = exprMethodRef.getDeclaringClass().getName();

        LineNumberTag tag = (LineNumberTag) stmt.getTag("LineNumberTag");
        if (tag != null)
            _db.add(METHOD_INV_LINE, insn, str(tag.getLineNumber()));

        String methodId = ii.methodId;
        if (expr instanceof DynamicInvokeExpr)
            writeDynamicInvoke((DynamicInvokeExpr) expr, ii.index, insn, methodId);
        else {
            String methodSig = invokeMethodSig(insn, declClass, simpleName, exprMethodRef, expr);
            if (expr instanceof StaticInvokeExpr)
                _db.add(STATIC_METHOD_INV, insn, str(ii.index), methodSig, methodId);
            else if (expr instanceof VirtualInvokeExpr || expr instanceof InterfaceInvokeExpr)
                _db.add(VIRTUAL_METHOD_INV, insn, str(ii.index), methodSig, _rep.local(methodId, (Local) ((InstanceInvokeExpr) expr).getBase()), methodId);
            else if (expr instanceof SpecialInvokeExpr)
                _db.add(SPECIAL_METHOD_INV, insn, str(ii.index), methodSig, _rep.local(methodId, (Local) ((InstanceInvokeExpr) expr).getBase()), methodId);
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

    private Value writeImmediate(Stmt stmt, String methodId,
                                 Value v, Type vType, SessionCounter session) {
        if (v instanceof Constant) {
            if (v instanceof StringConstant)
                v = writeStringConstantExpression(stmt, methodId, (StringConstant) v, session);
            else if (v instanceof ClassConstant)
                v = writeClassConstantExpression(methodId, (ClassConstant) v, session);
            else if (v instanceof NumericConstant)
                v = writeNumConstantExpression(methodId, (NumericConstant) v, vType, session);
            else
                System.err.println("ERROR: unknown type of immediate: " + v.getClass());
        }
        return v;
    }

    void writeAssignBinop(InstrInfo ii, Local left, BinopExpr right) {
        String methodId = ii.methodId;
        String insn = ii.insn;
        writeAssignBinop(insn, ii.index, _rep.local(methodId, left), methodId);

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
            writeAssignOperFrom(insn, L_OP, _rep.local(methodId, op1));
        } else if (right.getOp1() instanceof NumericConstant) {
            NumericConstant cons = (NumericConstant) right.getOp1();
            writeAssignOperFromConstant(insn, L_OP, cons.toString());
        }

        if (right.getOp2() instanceof Local) {
            Local op2 = (Local) right.getOp2();
            writeAssignOperFrom(insn, R_OP, _rep.local(methodId, op2));
        } else if (right.getOp2() instanceof NumericConstant) {
            NumericConstant cons = (NumericConstant) right.getOp2();
            writeAssignOperFromConstant(insn, R_OP, cons.toString());
        }
    }

    void writeAssignUnop(InstrInfo ii, Local left, UnopExpr right) {
        String methodId = ii.methodId;
        String insn = ii.insn;
        writeAssignUnop(insn, ii.index, _rep.local(methodId, left), methodId);

        if (right instanceof LengthExpr)
            writeOperatorAt(insn, "len");
        else if (right instanceof NegExpr)
            writeOperatorAt(insn, "~");
        else
            writeOperatorAt(insn, "??");

        if (right.getOp() instanceof Local) {
            Local op = (Local) right.getOp();
            writeAssignOperFrom(insn, L_OP, _rep.local(methodId, op));
        } else if (right.getOp() instanceof NumericConstant) {
            NumericConstant cons = (NumericConstant) right.getOp();
            writeAssignOperFromConstant(insn, L_OP, cons.toString());
        }
    }

    void writeAssignInstanceOf(InstrInfo ii, Local to, Local from, Type t) {
        String methodId = ii.methodId;
        _db.add(ASSIGN_INSTANCE_OF, ii.insn, str(ii.index), _rep.local(methodId, from), _rep.local(methodId, to), writeType(t), methodId);
    }

    void writeAssignPhantomInvoke(InstrInfo ii) {
        _db.add(ASSIGN_PHANTOM_INVOKE, ii.insn, str(ii.index), ii.methodId);
    }

    void writeBreakpointStmt(InstrInfo ii) {
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
                String tagType = null;
                if (tag instanceof IntegerConstantValueTag)
                    tagType = "int";
                else if (tag instanceof DoubleConstantValueTag)
                    tagType = "double";
                else if (tag instanceof LongConstantValueTag)
                    tagType = "long";
                else if (tag instanceof FloatConstantValueTag)
                    tagType = "float";
                if (tagType != null) {
                    // Trim last non-digit qualifier (e.g. 'L' in long constants).
                    int len = val.length();
                    if (!Character.isDigit(val.charAt(len-1)))
                        val = val.substring(0, len-1);
                    writeNumConstantRaw(val, tagType);
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
        SigInfo(SootMethodInterface ref, boolean reverse) {
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

}
