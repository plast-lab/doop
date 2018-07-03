package org.clyze.doop.python;

import com.ibm.wala.analysis.typeInference.TypeInference;
import com.ibm.wala.cast.ir.ssa.*;
import com.ibm.wala.cast.loader.CAstAbstractModuleLoader;
import com.ibm.wala.cast.python.ssa.PythonInvokeInstruction;
import com.ibm.wala.cast.python.types.PythonTypes;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.IAnalysisCacheView;
import com.ibm.wala.ssa.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.clyze.doop.python.utils.PythonIRPrinter;
import org.clyze.doop.wala.Local;
import org.clyze.doop.wala.Session;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import static org.clyze.doop.python.utils.PythonUtils.createLocal;

public class PythonFactGenerator implements Runnable{
    protected Log logger;

    private PythonFactWriter _writer;
    private IAnalysisCacheView cache;
    private PythonIRPrinter IRPrinter;
    private Set<IClass> _iClasses;

    //The classes that are in the class hierarchy by default
    //Useful on our current one-classhierarchy-per-file approach
    private static final String[] DEFAULT_CLASSES = new String[]{"list", "Root", "Exception", "object", "CodeBody", "trampoline"};
    private static final Set<String> defaultClasses = new HashSet<>(Arrays.asList(DEFAULT_CLASSES));;

    PythonFactGenerator(PythonFactWriter writer, Set<IClass> iClasses, String outDir, IAnalysisCacheView analysisCache)
    {
        this._writer = writer;
        this.logger = LogFactory.getLog(getClass());
        this._iClasses = iClasses;
        cache = analysisCache;
        IRPrinter = new PythonIRPrinter(cache, outDir);
    }

    @Override
    public void run() {

        for (IClass iClass : _iClasses) {
            String cName = iClass.getName().toString().substring(1);
            String[] classNameParts = cName.split("/");
            String declaringModule;
            //System.out.println(cName);
            if(iClass instanceof CAstAbstractModuleLoader.CoreClass) {
                String className;
                if(classNameParts.length == 2){
                    declaringModule = classNameParts[0].replace("script ","");
                    className = classNameParts[1];
                }
                else{
                    declaringModule = "BUILTIN";
                    className = classNameParts[0];
                }
                System.out.println("Adding Class <" + declaringModule + ":" + className + ">");
                if(defaultClasses.contains(className))
                    continue;
                iClass.getAllFields().forEach(this::generate);
                _writer.writeClassOrInterfaceType(iClass);
//
//            // the isInterface condition prevents Object as superclass of interface
                if (iClass.getSuperclass() != null && !iClass.isInterface()) {
                    _writer.writeDirectSuperclass(iClass, iClass.getSuperclass());
                    System.out.println("Class " + className +" extends " + iClass.getSuperclass().getName().toString().substring(1));
                }

            }else if (iClass instanceof CAstAbstractModuleLoader.DynamicCodeBody) {

//                declaringModule = classNameParts[0].replace("script ","");
//                if(classNameParts.length >= 3){
//                    String parClassName = "L" +classNameParts[0];
//                    for(int i=1; i<classNameParts.length -1; i++)
//                        parClassName += "/" + classNameParts[i];
//                    TypeReference type = TypeReference.find(PythonTypes.pythonLoader, parClassName);
//                    IClass decClass = iClass.getClassHierarchy().lookupClass(type);
//                    if(decClass instanceof CAstAbstractModuleLoader.CoreClass){
//                        String declaringClass = classNameParts[1];
//                        String methodName = classNameParts[2];
//                        System.out.println("Adding Method <" + declaringModule + ":" + declaringClass + ":" + methodName + ">");
//                    }else{
//                        String outerFunct = classNameParts[1];
//                        String methodName = classNameParts[2];
//                        System.out.println("Adding Inner Function <" + declaringModule + ":" + outerFunct + ":" + methodName + ">");
//                    }
//                }
//                else{
//                    System.out.println("Adding Function  <" + declaringModule + ":" +  classNameParts[classNameParts.length - 1] + ">");
//                }

            }else{
                System.out.println("Uknown type of Class " + cName + " object type: " + iClass.getClass().getName());
                throw new RuntimeException(":(");
            }

            for (IMethod m : iClass.getDeclaredMethods()) {
                Session session = new org.clyze.doop.wala.Session();
                try {
                    generate(m, session);
                }
                catch (Exception exc) {
                    System.err.println("Error while processing method: " + cName);
                    exc.printStackTrace();
                    throw exc;
                }
            }
        }
    }

    private void generate(IField f) {
        System.out.println("GENERATING FIELD!! "+f.getName());
        _writer.writeField(f);
    }

    private void generate(IMethod m, Session session) {
        _writer.writeMethod(m);
        int paramIndex = 0;


        if(!m.isStatic())
        {
            //_writer.writeThisVar(m);    //Currently doesn't make sense so it is commented out. "self" is on v2
            paramIndex = 1;
        }

        IR ir = cache.getIR(m);

        while (paramIndex < m.getNumberOfParameters()) {
            if (m.isStatic()) { //Currently not supported by WALAs front end but hopefully we can ask it from them.
                _writer.writeFormalParam(m, ir, paramIndex, paramIndex);
            }
            else {
                _writer.writeFormalParam(m, ir, paramIndex, paramIndex - 1);
            }
            paramIndex++;
        }

        generate(m, ir, session);
    }

    private void generate(IMethod m, IR ir, Session session) {
        SSAInstruction[] instructions = ir.getInstructions();
        SSACFG cfg = ir.getControlFlowGraph();
        SSACFG.ExceptionHandlerBasicBlock previousHandlerBlock = null;
        TypeInference typeInference = null;

        //typeInference = AstTypeInference.make(ir, true);


        for (int i = 0; i <= cfg.getMaxNumber(); i++) {
            SSACFG.BasicBlock basicBlock = cfg.getNode(i);
            int start = basicBlock.getFirstInstructionIndex();
            int end = basicBlock.getLastInstructionIndex();

            Iterator<SSAPhiInstruction> phis = basicBlock.iteratePhis();
            while (phis.hasNext()) {
                SSAPhiInstruction phiInstruction = phis.next();
                //this.generateDefs(m, ir, phiInstruction, typeInference);
                //this.generateUses(m, ir, phiInstruction, session, typeInference);
                //generate(m, ir, phiInstruction, session, typeInference);
            }

            if (basicBlock instanceof SSACFG.ExceptionHandlerBasicBlock) {
                if (((SSACFG.ExceptionHandlerBasicBlock) basicBlock).getCatchInstruction() == null) {
                    continue;
                }
                //generateDefs(m, ir, ((SSACFG.ExceptionHandlerBasicBlock) basicBlock).getCatchInstruction(), typeInference);
                //session.calcInstructionNumber(((SSACFG.ExceptionHandlerBasicBlock) basicBlock).getCatchInstruction());
                //_writer.writeUnsupported(m, ir, ((SSACFG.ExceptionHandlerBasicBlock) basicBlock).getCatchInstruction(), session);
            }

            for (int j = start; j <= end; j++) {
                if (instructions[j] != null) {
                    this.generateDefs(m, ir, instructions[j], typeInference);
                    this.generateUses(m, ir, instructions[j], session, typeInference);
                    if (instructions[j] instanceof SSAReturnInstruction) {
                        generate(m, ir, (SSAReturnInstruction) instructions[j], session, typeInference);
                    } else if (instructions[j] instanceof SSAUnaryOpInstruction) {
                        generate(m, ir, (SSAUnaryOpInstruction) instructions[j], session, typeInference);
                    } else if (instructions[j] instanceof SSABinaryOpInstruction) {
                        generate(m, ir, (SSABinaryOpInstruction) instructions[j], session, typeInference);
                    } else if (instructions[j] instanceof SSAThrowInstruction) {
                        generate(m, ir, (SSAThrowInstruction) instructions[j], session, typeInference);
                    } else if (instructions[j] instanceof PythonInvokeInstruction) {
                        generate(m, ir, (PythonInvokeInstruction) instructions[j], session, typeInference);
                    } else if (instructions[j] instanceof SSAAbstractInvokeInstruction) {
                        generate(m, ir, (SSAAbstractInvokeInstruction) instructions[j], session, typeInference);
                    } else if (instructions[j] instanceof EachElementGetInstruction) {
                        generate(m, ir, (EachElementGetInstruction) instructions[j], session, typeInference);
                    } else if (instructions[j] instanceof AstLexicalAccess) {
                        generate(m, ir, (AstLexicalAccess) instructions[j], session, typeInference);
                    } else if (instructions[j] instanceof ReflectiveMemberAccess) { // PythonPropertyWrite, PythonPropertyRead
                        generate(m, ir, (ReflectiveMemberAccess) instructions[j], session, typeInference);
                    } else if (instructions[j] instanceof AstGlobalRead) {
                        generate(m, ir, (AstGlobalRead) instructions[j], session, typeInference);
                    } else if (instructions[j] instanceof SSAGetInstruction) {
                        //System.out.println("get" + instructions[j].toString() + " - " + instructions[j].getClass().getName());
                        generate(m, ir, (SSAGetInstruction) instructions[j], session, typeInference);
                    } else if (instructions[j] instanceof AstGlobalWrite) {
                        generate(m, ir, (AstGlobalWrite) instructions[j], session, typeInference);
                    } else if (instructions[j] instanceof SSAPutInstruction) {
                        //System.out.println("oi" + instructions[j].toString() + " - " + instructions[j].getClass().getName());
                        generate(m, ir, (SSAPutInstruction) instructions[j], session, typeInference);
                    } else if (instructions[j] instanceof SSANewInstruction) {
                        generate(m, ir, (SSANewInstruction) instructions[j], session, typeInference);
                    } else if (instructions[j] instanceof SSAComparisonInstruction) {
                        generate(m, ir, (SSAComparisonInstruction) instructions[j], session, typeInference);
                    } else if (instructions[j] instanceof SSASwitchInstruction) {
                        session.calcInstructionNumber(instructions[j]);
                    } else if (instructions[j] instanceof SSAGotoInstruction) {
                        session.calcInstructionNumber(instructions[j]);
                    } else if (instructions[j] instanceof SSAConditionalBranchInstruction) {
                        session.calcInstructionNumber(instructions[j]);
                    } else {
                        System.out.println("Unknown instruction: " +instructions[j].toString(ir.getSymbolTable()) + " of type: " + instructions[j].getClass().getName());
                    }
                }
            }

            Iterator<SSAPiInstruction> pis = basicBlock.iteratePis();
            while (pis.hasNext()) {
                SSAPiInstruction piInstruction = pis.next();
            }
        }

        for (int i = 0; i <= cfg.getMaxNumber(); i++) {
            SSACFG.BasicBlock basicBlock = cfg.getNode(i);
            int start = basicBlock.getFirstInstructionIndex();
            int end = basicBlock.getLastInstructionIndex();

            for (int j = start; j <= end; j++) {
                if (instructions[j] instanceof SSASwitchInstruction) {
                    //generate(m, ir, (SSASwitchInstruction) instructions[j], session, typeInference);
                } else if (instructions[j] instanceof SSAGotoInstruction) {
                    //generate(m, ir, (SSAGotoInstruction) instructions[j], session);
                } else if (instructions[j] instanceof SSAConditionalBranchInstruction) {
                    //generate(m, ir, (SSAConditionalBranchInstruction) instructions[j], session, typeInference);
                }
            }
        }

        for (int i = 0; i <= cfg.getMaxNumber(); i++) {
            SSACFG.BasicBlock basicBlock = cfg.getNode(i);
            if (basicBlock instanceof SSACFG.ExceptionHandlerBasicBlock) {

            }
        }
    }

    public void generate(IMethod m, IR ir, SSAUnaryOpInstruction instruction, Session session, TypeInference typeInference) {
        // Unary op instructions have a single def (to) and a single use (from)
        Local to = createLocal(ir, instruction, instruction.getDef(), typeInference);
        Local from = createLocal(ir, instruction, instruction.getUse(0), typeInference);

        _writer.writeAssignUnop(m, instruction, to, from, session);
    }

    private void generate(IMethod m, IR ir, SSABinaryOpInstruction instruction, Session session, TypeInference typeInference)
    {
        // Binary instructions have two uses and a single def
        Local l = createLocal(ir, instruction, instruction.getDef(), typeInference);
        Local op1 = createLocal(ir, instruction, instruction.getUse(0), typeInference);
        Local op2 = createLocal(ir, instruction, instruction.getUse(1), typeInference);

        _writer.writeAssignBinop(m, instruction, l, op1, op2, session);
    }
    private void generate(IMethod m, IR ir, SSAComparisonInstruction instruction, Session session, TypeInference typeInference)
    {
        // Binary instructions have two uses and a single def
        Local l = createLocal(ir, instruction, instruction.getDef(), typeInference);
        Local op1 = createLocal(ir, instruction, instruction.getUse(0), typeInference);
        Local op2 = createLocal(ir, instruction, instruction.getUse(1), typeInference);

        _writer.writeAssignComparison(m, instruction, l, op1, op2, session);
    }

    private void generate(IMethod inMethod, IR ir, SSAThrowInstruction instruction, Session session, TypeInference typeInference)
    {
        // Throw instructions have a single use and no defs
        SymbolTable symbolTable = ir.getSymbolTable();
        int use = instruction.getUse(0);

        if(symbolTable.isNullConstant(use))
        {
            _writer.writeThrowNull(inMethod, instruction, session);
        }
        else
        {
            Local l = createLocal(ir, instruction, use, typeInference);
            _writer.writeThrow(inMethod, instruction, l, session);
        }
    }

    public void generate(IMethod m, IR ir, SSAPhiInstruction instruction, Session session, TypeInference typeInference) {
        // Phi instructions have a single def (to) and a number uses that represent the alternative values
        Local to = createLocal(ir, instruction, instruction.getDef(), typeInference);
        Local alternative;
        for(int i=0; i < instruction.getNumberOfUses();i++)
        {
            if (instruction.getUse(i) > -1) {
                alternative = createLocal(ir, instruction, instruction.getUse(i), typeInference);
            }
            else {
                continue;
            }
            _writer.writeAssignLocal(m, instruction, to, alternative, session);
        }
    }

    public void generate(IMethod m, IR ir, SSANewInstruction instruction, Session session, TypeInference typeInference) {
        Local l = createLocal(ir,instruction,instruction.getDef(),typeInference);
        int numOfUses = instruction.getNumberOfUses();
        if(numOfUses < 2)
        {
            _writer.writeAssignHeapAllocation(ir, m, instruction, l, session);
        }
        else
        {
            throw new RuntimeException("Unsupported new instr " + instruction.toString(ir.getSymbolTable()));
            //_writer.writeAssignNewMultiArrayExpr(ir, m, instruction, l, session);
        }
    }

    public void generate(IMethod m, IR ir, EachElementGetInstruction instruction, Session session, TypeInference typeInference){
        Local target = createLocal(ir,instruction,instruction.getDef(),typeInference);
        Local iter = createLocal(ir,instruction,instruction.getUse(0),typeInference);
        _writer.writeEachElementGet(m, instruction, target, iter, session);
    }

    public void generate(IMethod m, IR ir, AstLexicalAccess instruction, Session session, TypeInference typeInference){
        Local l;
        if(instruction.getAccessCount() !=1)
            System.out.println("instruction: " + instruction.toString(ir.getSymbolTable()) + " has more than one access!!!");
        l = createLocal(ir, instruction, instruction.getAccess(0).valueNumber, typeInference);
        _writer.writeLexicalAccess(m, instruction, l, session);
    }

    public void generate(IMethod m, IR ir, ReflectiveMemberAccess instruction, Session session, TypeInference typeInference){
        _writer.writeReflectiveAccess(m, ir, instruction, session, typeInference);
    }

    public void generate(IMethod m, IR ir, AstGlobalRead instruction, Session session, TypeInference typeInference) {
        Local to = createLocal(ir, instruction, instruction.getDef(), typeInference);
        String globalName = instruction.getGlobalName();
        if(globalName.startsWith("global "))
            globalName = globalName.substring(7);
        _writer.writeGlobalRead(m, instruction, to, globalName, session);

    }

    public void generate(IMethod m, IR ir, SSAGetInstruction instruction, Session session, TypeInference typeInference) {
        Local to = createLocal(ir, instruction, instruction.getDef(), typeInference);

        if (instruction.isStatic()) {
            throw new RuntimeException("Unexpected static get " + instruction.toString(ir.getSymbolTable()));
            //Get static field has no uses and a single def (to)
            //_writer.writeLoadStaticField(m, instruction, instruction.getDeclaredField(), to, session);
        }
        //Get instance field has one use (base) and one def (to)
        Local base = createLocal(ir, instruction, instruction.getUse(0), typeInference);
        _writer.writeLoadInstanceField(m, instruction, instruction.getDeclaredField(), base, to, session);

    }

    public void generate(IMethod m, IR ir, AstGlobalWrite instruction, Session session, TypeInference typeInference) {

        Local from = createLocal(ir, instruction, instruction.getUse(0), typeInference);
        String globalName = instruction.getGlobalName();
        if(globalName.startsWith("global "))
            globalName = globalName.substring(7);
        _writer.writeGlobalWrite(m, instruction, from, globalName, session);

    }

    public void generate(IMethod m, IR ir, SSAPutInstruction instruction, Session session, TypeInference typeInference) {

        if (instruction.isStatic()) {
            throw new RuntimeException("Unexpected static put " + instruction.toString(ir.getSymbolTable()));
            //Put static field has a single use (from) and no defs
            //Local from = createLocal(ir, instruction, instruction.getUse(0), typeInference);
            //_writer.writeStoreStaticField(m, instruction, instruction.getDeclaredField(), from, session);
        }
        //Put instance field has two uses (base and from) and no defs
        Local base = createLocal(ir, instruction, instruction.getUse(0), typeInference);
        Local from = createLocal(ir, instruction, instruction.getUse(1), typeInference);
        _writer.writeStoreInstanceField(m, instruction, instruction.getDeclaredField(), base, from, session);

    }

    public void generate(IMethod m, IR ir, PythonInvokeInstruction instruction, Session session, TypeInference typeInference) {
        // For invoke instructions the number of uses is equal to the number of parameters

        Local l;
        if(instruction.getNumberOfReturnValues() == 0)
            l = null;
        else
            l = createLocal(ir, instruction, instruction.getDef(), typeInference);
        _writer.writePythonInvoke(m, ir, instruction, l, session,typeInference);
    }

    public void generate(IMethod m, IR ir, SSAAbstractInvokeInstruction instruction, Session session, TypeInference typeInference) {
        // For invoke instructions the number of uses is equal to the number of parameters

        Local l;
        if(instruction.getNumberOfReturnValues() == 0)
            l = null;
        else
            l = createLocal(ir, instruction, instruction.getDef(), typeInference);
        _writer.writeInvoke(m, ir, instruction, l, session,typeInference);
    }

    /**
     * Return statement
     */
    private void generate(IMethod m, IR ir, SSAReturnInstruction instruction, Session session, TypeInference typeInference)
    {
        if (instruction.returnsVoid()) {
            // Return void has no uses
            _writer.writeReturnVoid(m, instruction, session);
        }
        else {
            // Return something has a single use
            Local l = createLocal(ir, instruction, instruction.getUse(0), typeInference);
            _writer.writeReturn(m, instruction, l, session);
        }
    }

    // Instructions have zero to two defs.According to WALA's documentation:
    // "SSAInvokeInstructions may additionally def a second variable, representing the exceptional return value."
    // For each def we use writeLocal to produce VAR_TYPE and VAR_DECLARING_TYPE facts.
    //We probably don't need to produce facts for it the second def.
    private void generateDefs(IMethod m, IR ir, SSAInstruction instruction, TypeInference typeInference) {

        if (instruction.hasDef()) {
            for (int i = 0; i < instruction.getNumberOfDefs(); i++) {
                int def = instruction.getDef(i);
                String[] names = null;
                Local l = createLocal(ir, instruction, def, typeInference);
                if(instruction.iindex > -1) {
                    names = ir.getLocalNames(instruction.iindex, def);
                    if(names.length > 0)
                        l.setSourceName(names[0]);
                }
                _writer.writeLocal(m, l);
            }
        }
    }

    //We only need to generate VAR_TYPE and VAR_DECLARING_TYPE facts for the used variables if they are constants
    //The others have either been previous defs or method parameters/this so they have already had facts produced.
    private void generateUses(IMethod m, IR ir, SSAInstruction instruction, Session session, TypeInference typeInference) {
        SymbolTable symbolTable = ir.getSymbolTable();

        for (int i = 0; i < instruction.getNumberOfUses(); i++) {
            int use = instruction.getUse(i);
            if (use != -1 && symbolTable.isConstant(use)) {
                Local l = createLocal(ir, instruction, use, typeInference);
                Value v = symbolTable.getValue(use);
                generateConstant(m, ir, instruction, v, l, session);
            }
        }
    }

    private void generateConstant(IMethod m, IR ir, SSAInstruction instruction, Value v, Local l, Session session) {
        SymbolTable symbolTable = ir.getSymbolTable();

        String s = v.toString();
        if (v.isStringConstant()) {
            l.setType(PythonTypes.object);
            _writer.writeStringConstantExpression(ir, m, instruction, l, (ConstantValue) v, session);
        } else if (v.isNullConstant()) {
            _writer.writeNullExpression(m, instruction, l, session);
        } else if (symbolTable.isIntegerConstant(l.getVarIndex())) {
            l.setType(PythonTypes.object);
            _writer.writeNumConstantExpression(m, instruction, l, (ConstantValue) v, session);
        } else if (symbolTable.isFloatConstant(l.getVarIndex())) {
            l.setType(PythonTypes.object);
            _writer.writeNumConstantExpression(m, instruction, l, (ConstantValue) v, session);
        } else if (symbolTable.isBooleanConstant(l.getVarIndex())) {
            l.setType(PythonTypes.object);
            _writer.writeNumConstantExpression(m, instruction, l, (ConstantValue) v, session);
        }
    }

}
