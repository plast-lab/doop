package org.clyze.doop.python;

import com.ibm.wala.analysis.typeInference.TypeInference;
import com.ibm.wala.cast.ir.ssa.*;
import com.ibm.wala.cast.loader.AstMethod;
import com.ibm.wala.cast.loader.CAstAbstractModuleLoader;
import com.ibm.wala.cast.python.ssa.PythonInvokeInstruction;
import com.ibm.wala.cast.python.ssa.PythonPropertyRead;
import com.ibm.wala.cast.python.ssa.PythonPropertyWrite;
import com.ibm.wala.cast.python.types.PythonTypes;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.ssa.*;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;
import org.apache.log4j.Logger;
import org.clyze.doop.common.FactEncoders;
import org.clyze.doop.python.utils.PythonDatabase;
import org.clyze.doop.python.utils.PythonPredicateFile;
import org.clyze.doop.wala.Local;
import org.clyze.doop.wala.Session;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.clyze.doop.python.utils.PythonPredicateFile.*;
import static org.clyze.doop.python.utils.PythonUtils.fixNewType;
import static org.clyze.doop.python.utils.PythonUtils.fixType;
import static org.clyze.doop.python.utils.PythonUtils.createLocal;

class PythonFactWriter {
    private final PythonDatabase _db;
    private final PythonRepresentation _rep = PythonRepresentation.getRepresentation();

    // Map from WALA's JVM like type string to our format.
    // Used in writeType().
    private final Map<String, String> _typeMap = new ConcurrentHashMap<>();

    private final SortedSet<String> packages = Collections.synchronizedSortedSet(new TreeSet<>());

    // Used for logging various messages.
    private final Logger logger = Logger.getLogger(getClass());

    PythonFactWriter(PythonDatabase db) {
        this._db = db;
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

    void writeRootFolder(){
        try {
            _db.add(PROJECT_ROOT_FOLDER, getRoot());
        } catch (Exception ex) {
            System.err.println("ERROR: cannot write project root folder.");
            ex.printStackTrace();
        }
    }

    void writeMethod(IMethod m) {
        String result = _rep.signature(m);
        String simpleName = _rep.simpleName(m);
        String sourceFileName = _rep.sourceFileName(m);
        String par = result;
        if(par.contains(":"))
            par = result.substring(0, result.lastIndexOf(":")).concat(">");
        String arity = Integer.toString(m.getNumberOfParameters() - 1);
        if(m.isStatic())
            arity = Integer.toString(m.getNumberOfParameters());

        String cName = m.getDeclaringClass().getName().toString().substring(1);
        String[] classNameParts = cName.split("/");
        String declaringModule = classNameParts[0].replace("script ","");
        if(classNameParts.length >= 3){
            String parClassName = "L" +classNameParts[0];
            for(int i=1; i<classNameParts.length -1; i++)
                parClassName += "/" + classNameParts[i];
            TypeReference type = TypeReference.find(PythonTypes.pythonLoader, parClassName);
            IClass decClass = m.getDeclaringClass().getClassHierarchy().lookupClass(type);
            if(decClass instanceof CAstAbstractModuleLoader.CoreClass){
//                String declaringClass = classNameParts[1];
//                String methodName = classNameParts[2];
//                logger.info("Adding Method <" + declaringModule + ":" + declaringClass + ":" + methodName + ">");
                logger.info("Adding Method " + result);
            }else{
//                String outerFunct = classNameParts[1];
//                String methodName = classNameParts[2];
//                logger.info("Adding Inner Function <" + declaringModule + ":" + outerFunct + ":" + methodName + ">");
                logger.info("Adding Inner Function " + result);
            }
        }
        else{
//            logger.info("Adding Function  <" + declaringModule + ":" +  classNameParts[classNameParts.length - 1] + ">");
            logger.info("Adding Function " + result);
        }

        _db.add(STRING_RAW, result, result);
        _db.add(FUNCTION, result, simpleName, par, arity, sourceFileName);

        if(simpleName.startsWith("comprehension"))
            _db.add(COMPREHENSION_FUNCTION, result);

        if(result.equals(sourceFileName))
            writeGlobalFunction(m);

    }

    private void writeGlobalFunction(IMethod globalFun){
        String funOrFileRep = _rep.signature(globalFun);
        String fileDeclaredInFolder = funOrFileRep.substring(0, funOrFileRep.lastIndexOf("/")).concat(">");
        String fileName = funOrFileRep.substring(funOrFileRep.lastIndexOf("/") + 1, funOrFileRep.length() - 4);

        _db.add(GLOBAL_FUNCTION, funOrFileRep);
        _db.add(FILE_DECLAREDING_PACKAGE, funOrFileRep, fileName, fileDeclaredInFolder);

        logger.info("PACKAGE " + fileDeclaredInFolder + " RESULT " + packageExists(fileDeclaredInFolder));
        if(! packageExists(fileDeclaredInFolder)){
            addPackage(fileDeclaredInFolder);
            String declFolderDeclFolder = fileDeclaredInFolder.substring(0, fileDeclaredInFolder.lastIndexOf("/")).concat(">");
            String declFolderName = fileDeclaredInFolder.substring(fileDeclaredInFolder.lastIndexOf("/") + 1, fileDeclaredInFolder.length() - 1);
            _db.add(PACKAGE_DECLAREDING_PACKAGE, fileDeclaredInFolder, declFolderName, declFolderDeclFolder);
        }

    }

    public String writeField(IField f) {
        String fieldId = _rep.signature(f);
        _db.add(FIELD_SIGNATURE, fieldId, _rep.classType(f.getDeclaringClass()), _rep.simpleName(f));
        return fieldId;
    }

    String writeClassOrInterfaceType(IClass c) {
        String classStr = _rep.classType(c);
        _db.add(CLASS_TYPE, classStr);
        return classStr;
    }


    void writeDirectSuperclass(IClass sub, IClass sup) {
        _db.add(DIRECT_SUPER_CLASS, _rep.classType(sub), _rep.classType(sup));
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
//        if (t.isArrayType() && inMap == null) {
//            _db.add(ARRAY_TYPE, typeName);
//            TypeReference componentType = t.getArrayElementType();
//            _db.add(COMPONENT_TYPE, typeName, writeType(componentType));
//            writeClassHeap(_rep.classConstant(typeName), typeName);
//        }
//        else if (t.isPrimitiveType() || t.isReferenceType() || t.isClassType()) {
//
//        }
//        else {
//            throw new RuntimeException("Don't know what to do with type " + t);
//        }

        return typeName;
    }

    void writeAssignLocal(IMethod m, SSAInstruction instruction, Local to, Local from, Session session) {
        int index = session.calcInstructionNumber(instruction);
        String insn = _rep.instruction(m, instruction, session, index);
        String methodId = _rep.signature(m);

        _db.add(ASSIGN_LOCAL, insn, str(index), _rep.local(m, from), _rep.local(m, to), methodId);
    }

    void writeAssignHeapAllocation(IR ir, IMethod m, SSANewInstruction instruction, Local l, Session session) {
        String heap = _rep.heapAlloc(m, instruction, session);

        _db.add(NORMAL_HEAP, heap, fixNewType(m, instruction, instruction.getConcreteType()));

        int index = session.calcInstructionNumber(instruction);
        String insn = _rep.instruction(m, instruction, session, index);
        String methodId = _rep.signature(m);
        _db.add(ASSIGN_HEAP_ALLOC, insn, str(index), heap, _rep.local(m, l), methodId);
    }

    private void writeAssignStringConstant(IR ir, IMethod m, SSAInstruction instruction, Local l, ConstantValue s, Session session) {
        int index = session.calcInstructionNumber(instruction);
        String constant = s.getValue().toString();
        String heapId = writeStringConstant(constant);

        String insn = _rep.signature(m) + "/assign/" + index; // Not using _rep.instruction() because we do not want to be identified by our instr
        String methodId = _rep.signature(m);

        _db.add(ASSIGN_HEAP_ALLOC, insn, str(index), heapId, _rep.local(m, l), methodId);
    }

    private void writeAssignNone(IMethod m, SSAInstruction instruction, Local l, Session session) {
        int index = session.calcInstructionNumber(instruction);
        String insn = _rep.signature(m) + "/assign/" + index; // Not using _rep.instruction() because we do not want to be identified by our instr
        String methodId = _rep.signature(m);

        _db.add(ASSIGN_NONE, insn, str(index), _rep.local(m, l), methodId);
    }

    private void writeAssignBoolConstant(IMethod m, SSAInstruction instruction, Local l, ConstantValue constant, Session session) {
        int index = session.calcInstructionNumber(instruction);
        String insn = _rep.signature(m) + "/assign/" + index; // Not using _rep.instruction() because we do not want to be identified by our instr
        String methodId = _rep.signature(m);

        _db.add(ASSIGN_BOOL_CONST, insn, str(index), constant.toString().substring(1), _rep.local(m, l), methodId);
    }

    private void writeAssignIntConstant(IMethod m, SSAInstruction instruction, Local l, ConstantValue constant, Session session) {
        int index = session.calcInstructionNumber(instruction);
        String insn = _rep.signature(m) + "/assign/" + index; // Not using _rep.instruction() because we do not want to be identified by our instr
        String methodId = _rep.signature(m);

        _db.add(ASSIGN_INT_CONST, insn, str(index), constant.toString().substring(1), _rep.local(m, l), methodId);
    }

    private void writeAssignFloatConstant(IMethod m, SSAInstruction instruction, Local l, ConstantValue constant, Session session) {
        int index = session.calcInstructionNumber(instruction);
        String insn = _rep.signature(m) + "/assign/" + index; // Not using _rep.instruction() because we do not want to be identified by our instr
        String methodId = _rep.signature(m);

        _db.add(ASSIGN_FLOAT_CONST, insn, str(index), constant.toString().substring(1), _rep.local(m, l), methodId);
    }

    void writeStoreInstanceField(IMethod m, SSAInstruction instruction, FieldReference f, Local base, Local from, Session session) {
        writeInstanceField(m, instruction, f, base, from, session, STORE_INST_FIELD);
    }

    void writeEachElementGet(IMethod m, EachElementGetInstruction instruction, Local target, Local iterVar, Session session) {
        int index = session.calcInstructionNumber(instruction);
        String insn = _rep.instruction(m, instruction, session, index);
        String methodId = _rep.signature(m);
        _db.add(ITERATOR_GET_NEXT_PROPERTY_NAME, insn, str(index), _rep.local(m, target), _rep.local(m, iterVar), methodId);
    }

    void writeReflectiveAccess(IMethod m, IR ir, ReflectiveMemberAccess instruction, Session session, TypeInference typeInference) {
        int index = session.calcInstructionNumber(instruction);
        String insn = _rep.instruction(m, instruction, session, index);
        String methodId = _rep.signature(m);
        Local baseVar = createLocal(ir, instruction, instruction.getObjectRef(), typeInference);
        Local fieldVar = createLocal(ir, instruction, instruction.getMemberRef(), typeInference);
        Local l;
        if(instruction instanceof PythonPropertyWrite){
            l = createLocal(ir, instruction, instruction.getUse(2), typeInference);
            _db.add(REFLECTIVE_WRITE, insn, str(index), _rep.local(m, baseVar), _rep.local(m, fieldVar), _rep.local(m, l), methodId);
        }else if(instruction instanceof PythonPropertyRead){
            l = createLocal(ir, instruction, instruction.getDef(), typeInference);
            _db.add(REFLECTIVE_READ, insn, str(index), _rep.local(m, l), _rep.local(m, baseVar), _rep.local(m, fieldVar), methodId);
        }else{
            throw new RuntimeException("Unexpected ReflectiveMemberAccess subclass of type: "+ instruction.getClass().getName());
        }
    }

    void writeLexicalAccess(IMethod m, AstLexicalAccess instruction, Local l, Session session) {
        int index = session.calcInstructionNumber(instruction);
        String insn = _rep.instruction(m, instruction, session, index);
        String methodId = _rep.signature(m);
        String varName = instruction.getAccess(0).variableName;
        String varDefiner = instruction.getAccess(0).variableDefiner.substring(1);
        String definerFunctRepr = _rep.getSigByName(varDefiner);
        if(instruction instanceof AstLexicalWrite){
            _db.add(LEXICAL_WRITE, insn, str(index), varName, definerFunctRepr, _rep.local(m, l), methodId);
        }else if(instruction instanceof AstLexicalRead){
            _db.add(LEXICAL_READ, insn, str(index), _rep.local(m, l), varName, definerFunctRepr, methodId);
        }else{
            throw new RuntimeException("Unexpected AstLexicalAccess subclass of type: "+ instruction.getClass().getName());
        }
    }

    void writeGlobalRead(IMethod m, SSAInstruction instruction, Local to, String globalName, Session session) {
        int index = session.calcInstructionNumber(instruction);
        String insn = _rep.instruction(m, instruction, session, index);
        String methodId = _rep.signature(m);
        _db.add(GLOBAL_READ, insn, str(index), _rep.local(m, to), globalName, methodId);
    }

    void writeGlobalWrite(IMethod m, SSAInstruction instruction, Local from, String globalName, Session session) {
        int index = session.calcInstructionNumber(instruction);
        String insn = _rep.instruction(m, instruction, session, index);
        String methodId = _rep.signature(m);
        //TODO: Make sure this always works
        if(globalName.contains("_defaults_")){
            //logger.info("WE GOT YE " + globalName + " | " + methodId);
            String functionName = globalName.substring(0, globalName.indexOf("_defaults_"));
            String walaFormalIndex = globalName.substring(globalName.lastIndexOf('_') + 1);
            String functionSig = methodId.replace(">", ":" + functionName +">");
            int formalIndex = Integer.parseInt(walaFormalIndex) - 1;
            logger.info(functionSig + " | " + formalIndex + " | " + _rep.local(m, from));
            _db.add(FORMAL_PARAM_DEFAULT_VAR, functionSig, str(formalIndex), _rep.local(m, from));
        }
        _db.add(GLOBAL_WRITE, insn, str(index), globalName, _rep.local(m, from), methodId);
    }

    void writeLoadInstanceField(IMethod m, SSAInstruction instruction, FieldReference f, Local base, Local to, Session session) {
        writeInstanceField(m, instruction, f, base, to, session, LOAD_INST_FIELD);
    }

    private void writeInstanceField(IMethod m, SSAInstruction instruction, FieldReference f, Local base, Local var, Session session, PythonPredicateFile predicateFile) {
        int index = session.calcInstructionNumber(instruction);
        String insn = _rep.instruction(m, instruction, session, index);
        String methodId = _rep.signature(m);

        TypeReference declaringClass = f.getDeclaringClass();
        //String fieldId = _rep.signature(f, declaringClass);
        String fieldId = _rep.simpleName(f);
        try{
            Integer.parseInt(fieldId);
            _db.add(ORIGINAL_INT_CONSTANT, fieldId);
        } catch (Throwable t) {
            logger.debug("Cannot parse fieldId=" + fieldId + ": " + t.getMessage());
        }
        _db.add(predicateFile, insn, str(index), _rep.local(m, var), _rep.local(m, base), fieldId, methodId);
    }

    void writeReturn(IMethod m, SSAInstruction instruction, Local l, Session session) {
        int index = session.calcInstructionNumber(instruction);
        String insn = _rep.instruction(m, instruction, session, index);
        String methodId = _rep.signature(m);

        _db.add(RETURN, insn, str(index), _rep.local(m, l), methodId);
    }

    void writeReturnNone(IMethod m, SSAInstruction instruction, Session session) {
        int index = session.calcInstructionNumber(instruction);
        String insn = _rep.instruction(m, instruction, session, index);
        String methodId = _rep.signature(m);

        _db.add(RETURN_NONE, insn, str(index), methodId);
    }

    // The return var of native methods is exceptional, in that it does not
    // correspond to a return instruction.
    void writeNativeReturnVar(IMethod m) {
        String methodId = _rep.signature(m);

        String  var = _rep.nativeReturnVar(m);
        _db.add(NATIVE_RETURN_VAR, var, methodId);
        _db.add(VAR_DECLARING_FUNCTION, var, methodId);
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
        //_db.add(VAR_TYPE, thisVar, writeType(m.getReference().getDeclaringClass()));
        _db.add(VAR_DECLARING_FUNCTION, thisVar, methodId);
    }

    void writeFormalParam(IMethod m, IR ir, int paramIndex, int actualIndex) {
        String methodId = _rep.signature(m);
        String var = _rep.param(m, paramIndex);
        String paramName = "__NONAME__";
        _db.add(VAR_DECLARING_FUNCTION, var, methodId);
        String[] names = null;
        names = ir.getLocalNames(0, paramIndex + 1);
        if(names.length > 0) {
            _db.add(VAR_SOURCE_NAME, var, names[0]);
            paramName = names[0];
        }
        else{
            logger.info("This shouldn't(?) ever happen.");
        }
        _db.add(FORMAL_PARAM, str(actualIndex), paramName, methodId, var);
    }

    void writeLocal(IMethod m, Local l) {
        String local = _rep.local(m, l);
        if(l.getSourceName() != null)
            _db.add(VAR_SOURCE_NAME, local, l.getSourceName());
        _db.add(VAR_DECLARING_FUNCTION, local, _rep.signature(m));
    }

    void writeStringConstantExpression(IR ir, IMethod inMethod, SSAInstruction instruction, Local l, ConstantValue constant, Session session) {
        // introduce a new temporary variable
        writeLocal(inMethod, l);
        writeAssignStringConstant(ir, inMethod, instruction, l, constant, session);
    }

    void writeNoneExpression(IMethod inMethod, SSAInstruction instruction, Local l, Session session) {
        // introduce a new temporary variable
        writeLocal(inMethod, l);
        writeAssignNone(inMethod, instruction, l,session);
    }

    void writeIntConstantExpression(IMethod inMethod, SSAInstruction instruction, Local l, ConstantValue constant, Session session) {
        // introduce a new temporary variable
        writeLocal(inMethod, l);
        writeAssignIntConstant(inMethod, instruction, l, constant, session);
    }

    void writeBoolConstantExpression(IMethod inMethod, SSAInstruction instruction, Local l, ConstantValue constant, Session session) {
        // introduce a new temporary variable
        writeLocal(inMethod, l);
        writeAssignBoolConstant(inMethod, instruction, l, constant, session);
    }

    void writeFloatConstantExpression(IMethod inMethod, SSAInstruction instruction, Local l, ConstantValue constant, Session session) {
        // introduce a new temporary variable
        writeLocal(inMethod, l);
        writeAssignFloatConstant(inMethod, instruction, l, constant, session);
    }

    //TODO: This needs work for pythons positional params!!!!!!!!!!!!
    private void writeActualParams(IMethod inMethod, IR ir, PythonInvokeInstruction instruction, String invokeExprRepr, Session session, TypeInference typeInference) {
        int totalNumberOfParams = 0;
        if (instruction.isStatic()) {
            //for (int i = 0; i < instruction.getNumberOfParameters(); i++) {
            for (int i = 0; i < instruction.getNumberOfPositionalParameters(); i++) {
                totalNumberOfParams++;
                Local l = createLocal(ir, instruction, instruction.getUse(i), typeInference);
                _db.add(ACTUAL_POSITIONAL_PARAMETER, str(i), invokeExprRepr, _rep.local(inMethod, l));
            }
            List<String> keywords = instruction.getKeywords();
            for (int i = 0; i < instruction.getNumberOfKeywordParameters(); i++) {
                totalNumberOfParams++;
                Local l = createLocal(ir, instruction, instruction.getUse(i + instruction.getNumberOfPositionalParameters()), typeInference);
                _db.add(ACTUAL_KEYWORD_PARAMETER, str(i), invokeExprRepr,keywords.get(i), _rep.local(inMethod, l));
            }
        }
        else {
            //for (int i = 1; i < instruction.getNumberOfParameters(); i++) {
            for (int i = 1; i < instruction.getNumberOfPositionalParameters(); i++) {
                totalNumberOfParams++;
                Local l = createLocal(ir, instruction, instruction.getUse(i), typeInference);
                _db.add(ACTUAL_POSITIONAL_PARAMETER, str(i-1), invokeExprRepr, _rep.local(inMethod, l));
            }
            List<String> keywords = instruction.getKeywords();
            for (int i = 0; i < instruction.getNumberOfKeywordParameters(); i++) {
                totalNumberOfParams++;
                Local l = createLocal(ir, instruction, instruction.getUse(i + instruction.getNumberOfPositionalParameters()), typeInference);
                _db.add(ACTUAL_KEYWORD_PARAMETER, str(i-1), invokeExprRepr,keywords.get(i), _rep.local(inMethod, l));
            }
        }
        _db.add(FUNCTION_INV_TOTAL_PARAMS, invokeExprRepr, str(totalNumberOfParams));
    }

    void writeAssignComparison(IMethod m, SSAComparisonInstruction instruction, Local left, Local op1, Local op2, Session session) {
        int index = session.calcInstructionNumber(instruction);
        String insn = _rep.instruction(m, instruction, session, index);
        String methodId = _rep.signature(m);

        _db.add(ASSIGN_BINOP, insn, str(index), _rep.local(m, left), instruction.getOperator().toString(), methodId);

        _db.add(ASSIGN_OPER_FROM, insn, "1", _rep.local(m, op1));
        _db.add(ASSIGN_OPER_FROM, insn, "2", _rep.local(m, op2));

    }

    void writePrintStatement(IMethod m, IR ir, AstEchoInstruction instruction, Session session)
    {
        int index = session.calcInstructionNumber(instruction);
        String insn = _rep.instruction(m, instruction, session, index);
        String methodId = _rep.signature(m);
        _db.add(PRINT_STATEMENT, insn, str(index), methodId);

        int uses = instruction.getNumberOfUses();
        for(int i=0; i < uses; i++) {
            Local l = createLocal(ir,instruction, instruction.getUse(i), null);
            _db.add(PRINT_ARG, insn, _rep.local(m, l));
        }
    }

    void writeYieldStatement(IMethod m, IR ir, AstYieldInstruction instruction, Session session) {
        int index = session.calcInstructionNumber(instruction);
        String insn = _rep.instruction(m, instruction, session, index);
        String methodId = _rep.signature(m);
        _db.add(YIELD_STATEMENT, insn, str(index), methodId);

        int uses = instruction.getNumberOfUses();
        for (int i = 0; i < uses; i++) {
            Local l = createLocal(ir, instruction, instruction.getUse(i), null);
            _db.add(YIELD_ARG, insn, _rep.local(m, l));
        }
    }
    void writeAssignBinop(IMethod m, SSABinaryOpInstruction instruction, Local left, Local op1, Local op2, Session session) {
        int index = session.calcInstructionNumber(instruction);
        String insn = _rep.instruction(m, instruction, session, index);
        String methodId = _rep.signature(m);

        _db.add(ASSIGN_BINOP, insn, str(index), _rep.local(m, left), instruction.getOperator().toString(), methodId);

        _db.add(ASSIGN_OPER_FROM, insn, "1", _rep.local(m, op1));
        _db.add(ASSIGN_OPER_FROM, insn, "2", _rep.local(m, op2));

    }
    //
    void writeAssignUnop(IMethod m, SSAUnaryOpInstruction instruction, Local to, Local from, Session session) {
        int index = session.calcInstructionNumber(instruction);
        String insn = _rep.instruction(m, instruction, session, index);
        String methodId = _rep.signature(m);

        _db.add(ASSIGN_UNOP, insn, str(index), _rep.local(m, to), instruction.getOpcode().toString(), methodId);

        _db.add(ASSIGN_OPER_FROM, insn, "1", _rep.local(m, from));
    }

    void writePythonInvoke(IMethod inMethod, IR ir, PythonInvokeInstruction instruction, Local to, Session session, TypeInference typeInference) {
        String insn = writePythonInvokeHelper(inMethod, ir, instruction, session, typeInference);
        if(to != null)
            _db.add(ASSIGN_RETURN_VALUE, insn, _rep.local(inMethod, to));
    }

    private String writePythonInvokeHelper(IMethod inMethod, IR ir, PythonInvokeInstruction instruction, Session session, TypeInference typeInference) {
        String methodId = _rep.signature(inMethod);

        String insn = _rep.functionInvoke(inMethod, instruction, session);
        writeActualParams(inMethod, ir, instruction, insn, session, typeInference);

        int index = session.calcInstructionNumber(instruction);
        Local toVar = createLocal(ir,instruction,instruction.getDef(), typeInference);
        Local functionObject = createLocal(ir,instruction,instruction.getUse(0), typeInference);
        if (instruction.isDispatch()) {
            //logger.info("Virtual "+ instruction.toString(ir.getSymbolTable()));
            _db.add(FUNCTION_INV, insn, str(index), _rep.local(inMethod,toVar), _rep.local(inMethod, functionObject), methodId);
        }
        else
            throw new RuntimeException("Cannot handle invoke instruction: " + instruction.toString(ir.getSymbolTable()));

        return insn;
    }

    void writeInvoke(IMethod inMethod, IR ir, SSAAbstractInvokeInstruction instruction, Local to, Session session, TypeInference typeInference) {
        String insn = writeInvokeHelper(inMethod, ir, instruction, to, session, typeInference);
        if(to != null)
            _db.add(ASSIGN_RETURN_VALUE, insn, _rep.local(inMethod, to));
    }

    private String writeInvokeHelper(IMethod inMethod, IR ir, SSAAbstractInvokeInstruction instruction, Local to, Session session, TypeInference typeInference) {
        String methodId = _rep.signature(inMethod);

        MethodReference targetRef = instruction.getCallSite().getDeclaredTarget();
        //writeActualParams(inMethod, ir, instruction, insn, session,typeInference); //IF THIS EVER GETS COMMENTED OUT index sequence will break.

        int index = session.calcInstructionNumber(instruction);
        String insn = _rep.instruction(inMethod, instruction, session, index);

        if (instruction.isStatic()) {
            if(targetRef.getName().toString().equals("import")){
                String module = fixType(targetRef.getReturnType()).replace('/','.');
                _db.add(IMPORT, insn, str(index), module, _rep.local(inMethod,to), methodId);
            }
            else{
                throw new RuntimeException("Unexpected invoke instruction(non-import static): " + instruction);
            }
            //_db.add(STATIC_METHOD_INV, insn, _rep.signature(targetRef), methodId);
        }
        else {
            throw new RuntimeException("Cannot handle invoke instruction: " + instruction.toString(ir.getSymbolTable()));
        }

        return insn;
    }

    void writeFunctionSourcePosition(AstMethod meth){
        IMethod.SourcePosition sourceInfo = meth.getSourcePosition();
        int firstLine = 0, firstColumn =0 ,lastLine =0 ,lastColumn =0;
        String function = _rep.signature(meth);
        if (sourceInfo != null) {
            firstLine = sourceInfo.getFirstLine();
            firstColumn = sourceInfo.getFirstCol();
            lastLine = sourceInfo.getLastLine();
            lastColumn = sourceInfo.getLastCol();
        }
        _db.add(FUNCTION_SOURCE_POSITION, function, str(firstLine),str(firstColumn),str(lastLine),str(lastColumn));
    }

    void writeInstructionSourcePosition(IMethod inMethod, IR ir, SSAInstruction instruction, Session session)
    {
        int index = session.getMaxInstructionNumber(instruction);
        String insn = _rep.instruction(inMethod,instruction, session, index);

        int firstLine = 0, firstColumn =0 ,lastLine =0 ,lastColumn =0;
        if(instruction.iindex != -1) {
            try {
                IMethod.SourcePosition sourceInfo = ir.getMethod().getSourcePosition(instruction.iindex);
                if (sourceInfo != null) {
                    firstLine = sourceInfo.getFirstLine();
                    firstColumn = sourceInfo.getFirstCol();
                    lastLine = sourceInfo.getLastLine();
                    lastColumn = sourceInfo.getLastCol();
                }
            } catch (InvalidClassFileException e) {
                logger.debug("Invalid class file: " + e.getMessage());
            }
        }
        _db.add(INSTRUCTION_SOURCE_POSITION, insn, str(firstLine),str(firstColumn),str(lastLine),str(lastColumn));
    }

    void writeError(PythonPredicateFile predFile, String fileName, String... args)
    {
        _db.add(predFile, fileName, args);
    }

    private boolean packageExists(String pack){
        return packages.contains(pack);
    }

    private void addPackage(String pack){
        packages.add(pack);
    }

    private String getRoot(){
        //return packages.first();
        return packages.last();
    }
}
