package org.clyze.doop.python;

import com.ibm.wala.analysis.typeInference.TypeInference;
import com.ibm.wala.cast.python.ssa.PythonInvokeInstruction;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.ssa.*;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.types.annotations.Annotation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.clyze.doop.common.FactEncoders;
import org.clyze.doop.python.utils.PythonDatabase;
import org.clyze.doop.python.utils.PythonPredicateFile;
import org.clyze.doop.wala.Local;
import org.clyze.doop.wala.Session;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.clyze.doop.python.utils.PythonPredicateFile.*;
import static org.clyze.doop.python.utils.PythonUtils.fixType;
import static org.clyze.doop.python.utils.PythonUtils.createLocal;

public class PythonFactWriter {
    private PythonDatabase _db;
    private PythonRepresentation _rep;

    //Map from WALA's JVM like type string to our format
    //Used in writeType()
    private Map<String, String> _typeMap;

    //Used for logging various messages
    protected Log logger;
    PythonFactWriter(PythonDatabase db) {
        _db = db;
        _rep = PythonRepresentation.getRepresentation();
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

    String writeMethod(IMethod m) {
        String result = _rep.signature(m);
        String arity = Integer.toString(m.getNumberOfParameters() - 1);
        if(m.isStatic())
            arity = Integer.toString(m.getNumberOfParameters());

        _db.add(STRING_RAW, result, result);
        _db.add(METHOD, result, _rep.simpleName(m.getReference()), _rep.params(m.getReference()), writeType(m.getReference().getDeclaringClass()), writeType(m.getReturnType()), m.getDescriptor().toUnicodeString(), arity);
        for (Annotation annotation : m.getAnnotations()) {
            _db.add(METHOD_ANNOTATION, result, fixType(annotation.getType()));
            //TODO:See if we can take use other features wala offers for annotations (named and unnamed arguments)
        }
        return result;
    }

    void writeClassOrInterfaceType(IClass c) {
        String classStr = fixType(c.getReference());
        if (c.isInterface()) {
            _db.add(INTERFACE_TYPE, classStr);
        }
        else {
            _db.add(CLASS_TYPE, classStr);
        }
        _db.add(CLASS_HEAP, _rep.classConstant(c), classStr);

        Collection<Annotation> annotations = c.getAnnotations();
        if (annotations != null) {
            for (Annotation annotation : annotations) {
                _db.add(CLASS_ANNOTATION, classStr, fixType(annotation.getType()));
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
        return fixType(c.getReference());
    }

    private String writeType(TypeReference t) {
        String inMap = _typeMap.get(t.toString());
        String typeName;

        if(inMap == null)
        {
            typeName= fixType(t);
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
        int sourceLineNum;
        try {
            IMethod.SourcePosition sourceInfo = ir.getMethod().getSourcePosition(instruction.iindex);
            if(sourceInfo == null)
                sourceLineNum = 0;
            else{
                sourceLineNum = sourceInfo.getFirstLine();
            }
        } catch (InvalidClassFileException e) {
            sourceLineNum = 0;
        }

        return sourceLineNum;
    }

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

    void writeStoreInstanceField(IMethod m, SSAInstruction instruction, FieldReference f, Local base, Local from, Session session) {
        writeInstanceField(m, instruction, f, base, from, session, STORE_INST_FIELD);
    }

    void writeLoadInstanceField(IMethod m, SSAInstruction instruction, FieldReference f, Local base, Local to, Session session) {
        writeInstanceField(m, instruction, f, base, to, session, LOAD_INST_FIELD);
    }

    private void writeInstanceField(IMethod m, SSAInstruction instruction, FieldReference f, Local base, Local var, Session session, PythonPredicateFile predicateFile) {
        int index = session.calcInstructionNumber(instruction);
        String insn = _rep.instruction(m, instruction, session, index);
        String methodId = _rep.signature(m);

        //TypeReference declaringClass = getCorrectFieldDeclaringClass(f, m.getClassHierarchy());
        TypeReference declaringClass = f.getDeclaringClass();
        String fieldId = _rep.signature(f, declaringClass);
        _db.add(predicateFile, insn, str(index), _rep.local(m, var), _rep.local(m, base), fieldId, methodId);
    }

    void writeStoreStaticField(IMethod m, SSAInstruction instruction, FieldReference f, Local from, Session session) {
        writeStaticField(m, instruction, f, from, session, STORE_STATIC_FIELD);
    }

    void writeLoadStaticField(IMethod m, SSAInstruction instruction, FieldReference f, Local to, Session session) {
        writeStaticField(m, instruction, f, to, session, LOAD_STATIC_FIELD);
    }

    private void writeStaticField(IMethod m, SSAInstruction stmt, FieldReference f, Local var, Session session, PythonPredicateFile predicateFile) {
        int index = session.calcInstructionNumber(stmt);
        String insn = _rep.instruction(m, stmt, session, index);
        String methodId = _rep.signature(m);

        //TypeReference declaringClass = getCorrectFieldDeclaringClass(f, m.getClassHierarchy());
        TypeReference declaringClass = f.getDeclaringClass();
        String fieldId = _rep.signature(f, declaringClass);
        _db.add(predicateFile, insn, str(index), _rep.local(m, var), fieldId, methodId);
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

    //TODO: This needs work for pythons positional params!!!!!!!!!!!!
    private void writeActualParams(IMethod inMethod, IR ir, SSAAbstractInvokeInstruction instruction, String invokeExprRepr, Session session, TypeInference typeInference) {
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
    }

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

    void writePythonInvoke(IMethod inMethod, IR ir, PythonInvokeInstruction instruction, Local to, Session session, TypeInference typeInference) {
        String insn = writePythonInvokeHelper(inMethod, ir, instruction, session, typeInference);
        if(to != null)
            _db.add(ASSIGN_RETURN_VALUE, insn, _rep.local(inMethod, to));
    }

    private String writePythonInvokeHelper(IMethod inMethod, IR ir, PythonInvokeInstruction instruction, Session session, TypeInference typeInference) {
        String methodId = _rep.signature(inMethod);

        int sourceLineNum = getLineNumberFromInstruction(ir,instruction);


        MethodReference targetRef = instruction.getCallSite().getDeclaredTarget();


        String insn = _rep.invoke(ir,inMethod, instruction, targetRef, session, typeInference);
        writeActualParams(inMethod, ir, instruction, insn, session,typeInference);

        int index = session.calcInstructionNumber(instruction);

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
        else {
            throw new RuntimeException("Cannot handle invoke instruction: " + instruction);
        }

        return insn;
    }

    void writeInvoke(IMethod inMethod, IR ir, SSAInvokeInstruction instruction, Local to, Session session, TypeInference typeInference) {
        String insn = writeInvokeHelper(inMethod, ir, instruction, session, typeInference);
        if(to != null)
            _db.add(ASSIGN_RETURN_VALUE, insn, _rep.local(inMethod, to));
    }

    private String writeInvokeHelper(IMethod inMethod, IR ir, SSAInvokeInstruction instruction, Session session, TypeInference typeInference) {
        String methodId = _rep.signature(inMethod);

        int sourceLineNum = getLineNumberFromInstruction(ir,instruction);


        MethodReference targetRef = instruction.getCallSite().getDeclaredTarget();


        String insn = _rep.invoke(ir,inMethod, instruction, targetRef, session, typeInference);
        writeActualParams(inMethod, ir, instruction, insn, session,typeInference);

        int index = session.calcInstructionNumber(instruction);

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
        else {
            throw new RuntimeException("Cannot handle invoke instruction: " + instruction);
        }

        return insn;
    }
}
