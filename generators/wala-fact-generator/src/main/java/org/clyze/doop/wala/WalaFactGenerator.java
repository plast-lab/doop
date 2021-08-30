package org.clyze.doop.wala;

import com.ibm.wala.analysis.typeInference.TypeInference;
import com.ibm.wala.classLoader.*;
import com.ibm.wala.dalvik.analysis.typeInference.DalvikTypeInference;
import com.ibm.wala.dalvik.classLoader.DexIMethod;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.IAnalysisCacheView;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.ssa.*;
import com.ibm.wala.types.TypeReference;
import org.apache.log4j.Logger;
import java.util.*;

import static org.clyze.doop.wala.WalaUtils.createLocal;
import static org.clyze.doop.wala.WalaUtils.fixTypeString;
import static org.clyze.doop.wala.WalaUtils.getNextNonNullInstruction;

/**
 * Traverses Soot classes and invokes methods in FactWriter to
 * generate facts. The class FactGenerator is the main class
 * controlling what facts are generated.
 */

class WalaFactGenerator implements Runnable {

    private final Logger logger = Logger.getLogger(getClass());

    private final WalaFactWriter _writer;
    private final Set<IClass> _iClasses;
    private AnalysisOptions options;
    private final boolean _android;
    private final IAnalysisCacheView cache;
    private final WalaIRPrinter IRPrinter;

    WalaFactGenerator(WalaFactWriter writer, Set<IClass> iClasses, String outDir, boolean androidAnalysis, IAnalysisCacheView analysisCache)
    {
        this._writer = writer;
        this._iClasses = iClasses;
        //options = new AnalysisOptions();
        //options.getSSAOptions().setPiNodePolicy(SSAOptions.getAllBuiltInPiNodes()); //CURRENTLY these are not active
        _android = androidAnalysis;
        cache = analysisCache;
//        if(androidAnalysis)
//            cache = new AnalysisCacheImpl(new DexIRFactory());
//        else
//            cache = new AnalysisCacheImpl();                //Without the SSaOptions -- piNodes
        //cache = new AnalysisCacheImpl(new DefaultIRFactory(), options.getSSAOptions()); //Change to this to make the IR according to the SSAOptions -- to include piNodes
        IRPrinter = new WalaIRPrinter(cache,outDir);
    }


    @Override
    public void run() {

        for (IClass iClass : _iClasses) {
            IRPrinter.printIR(iClass);
            _writer.writeClassOrInterfaceType(iClass);
            //TODO: Handling of Arrays?
            if(iClass.isAbstract())
                _writer.writeClassModifier(iClass, "abstract");
//            if(Modifier.isFinal(modifiers))
//                _writer.writeClassModifier(iClass, "final");
            if(iClass.isPublic())
                _writer.writeClassModifier(iClass, "public");
            if(iClass.isPrivate())
                _writer.writeClassModifier(iClass, "private");

            // the isInterface condition prevents Object as superclass of interface
            if (iClass.getSuperclass() != null && !iClass.isInterface()) {
                _writer.writeDirectSuperclass(iClass, iClass.getSuperclass());
            }

            for (IClass i : iClass.getAllImplementedInterfaces()) {
                _writer.writeDirectSuperinterface(iClass, i);
            }

            iClass.getDeclaredInstanceFields().forEach(this::generate);
            try {
                iClass.getDeclaredStaticFields().forEach(this::generate);
            } catch (NullPointerException exc) { //For some reason in DexClasses .getDeclaredStaticFields() can throw a NullPointerException
                System.err.println("Ignoring null exception when reading static fields");
            }


            for (IMethod m : iClass.getDeclaredMethods()) {
                Session session = new org.clyze.doop.wala.Session();
                try {
                    generate(m, session);
                } catch (Exception exc) {
                    System.err.println("Error while processing method: " + m + " of class " + m.getDeclaringClass());
                    exc.printStackTrace();
                    throw exc;
                }
            }
        }
    }

    private void generate(IField f)
    {
        _writer.writeField(f);
        _writer.writeFieldInitialValue(f); //TODO: Fix this

        if(f.isFinal())
            _writer.writeFieldModifier(f, "final");
        if(f.isPrivate())
            _writer.writeFieldModifier(f, "private");
        if(f.isProtected())
            _writer.writeFieldModifier(f, "protected");
        if(f.isPublic())
            _writer.writeFieldModifier(f, "public");
        if(f.isStatic())
            _writer.writeFieldModifier(f, "static");
//        if(Modifier.isSynchronized(modifiers))
//            _writer.writeFieldModifier(f, "synchronized");
//        if(Modifier.isTransient(modifiers))
//            _writer.writeFieldModifier(f, "transient");
        if(f.isVolatile())
            _writer.writeFieldModifier(f, "volatile");
        // TODO interface?
        // TODO strictfp?
        // TODO annotation?
        // TODO enum?
    }

    private void generate(IMethod m, Session session)
    {
        _writer.writeMethod(m);
        if(m.isAbstract())
            _writer.writeMethodModifier(m, "abstract");
        if(m.isFinal())
            _writer.writeMethodModifier(m, "final");
        if(m.isNative())
            _writer.writeMethodModifier(m, "native");
        if(m.isPrivate())
            _writer.writeMethodModifier(m, "private");
        if(m.isProtected())
            _writer.writeMethodModifier(m, "protected");
        if(m.isPublic())
            _writer.writeMethodModifier(m, "public");
        if(m.isStatic())
            _writer.writeMethodModifier(m, "static");
        if(m.isSynchronized())
            _writer.writeMethodModifier(m, "synchronized");
        // TODO would be nice to have isVarArgs in Wala
//        if(Modifier.isTransient(modifiers))
//            _writer.writeMethodModifier(m, "varargs");
        if(m.isSynchronized())
            _writer.writeMethodModifier(m, "volatile");
        if(m.isSynthetic())
            _writer.writeMethodModifier(m, "synthetic");
        if(m.isBridge())
            _writer.writeMethodModifier(m, "bridge");

        if(m.isNative())
        {
            _writer.writeNativeMethodId(m);
            _writer.writeNativeReturnVar(m);
        }
        int paramIndex = 0;
        if(!m.isStatic())
        {
            _writer.writeThisVar(m);
            paramIndex = 1;
        }
        if(isPhantomBased(m))
            _writer.writePhantomBasedMethod(m.getReference());

        while (paramIndex < m.getNumberOfParameters()) {
            if (m.isStatic() || m.isClinit()) {
                _writer.writeFormalParam(m, paramIndex, paramIndex);
            }
            else {
                _writer.writeFormalParam(m, paramIndex, paramIndex - 1);
            }
            paramIndex++;
        }

        try {
            if(m.getDeclaredExceptions()!= null) //Android can return null, java cannot
            {
//                if (m.isNative() && m.getDeclaredExceptions().length > 0) {
//                    List<String> declaredExceptions = new ArrayList<>();
//                    for (TypeReference exceptionType : m.getDeclaredExceptions()) {
//                        System.out.println("Method " + _writer.writeMethod(m) + " throws " + fixTypeString(exceptionType.toString()));
//                        declaredExceptions.add(fixTypeString(exceptionType.toString()));
//                    }
//                    _writer.addMockExceptionThrows(m.getReference(), declaredExceptions);
//                }
                for (TypeReference exceptionType : m.getDeclaredExceptions()) {
                    _writer.writeMethodDeclaresException(m, exceptionType);
                }
            }
        } catch (InvalidClassFileException e) {
            e.printStackTrace();
        }

        if(!(m.isAbstract() || m.isNative()))
        {
            try {
                IR ir = cache.getIR(m, Everywhere.EVERYWHERE);
                generate(m, ir, session);
            } catch (Throwable t){
                System.err.println("Ignoring exception: " + t.getMessage());
            }
        }
    }

    private void generate(IMethod m, IR ir, Session session)
    {
        SSAInstruction[] instructions = ir.getInstructions();
        SSACFG cfg = ir.getControlFlowGraph();
        SSACFG.ExceptionHandlerBasicBlock previousHandlerBlock = null;
        TypeInference typeInference;
        if(_android) { // Sometimes DalvikTypeInference fails due to assertion, so we try with normal TypeInference.
            try {
                typeInference = DalvikTypeInference.make(ir, true);
            }
            catch(Throwable er) {
                System.out.println("\nDalvik Type Inference failed for method: "+ m.getName().toString() + " of class: " + fixTypeString(m.getDeclaringClass().getName().toString()) + "\n");
                typeInference = null;
                //return;
            }
        }
        else
            typeInference = TypeInference.make(ir,true);

        WalaExceptionHelper walaExceptionHelper = new WalaExceptionHelper(instructions, m, cfg);


        for (int i = 0; i <= cfg.getMaxNumber(); i++) {
            SSACFG.BasicBlock basicBlock = cfg.getNode(i);
            int start = basicBlock.getFirstInstructionIndex();
            int end = basicBlock.getLastInstructionIndex();

            Iterator<SSAPhiInstruction> phis = basicBlock.iteratePhis();
            while(phis.hasNext())
            {
                SSAPhiInstruction phiInstruction = phis.next();
                this.generateDefs(m, ir, phiInstruction, typeInference);
                this.generateUses(m, ir, phiInstruction, session, typeInference);
                generate(m, ir, phiInstruction, session, typeInference);
            }

            if (basicBlock instanceof SSACFG.ExceptionHandlerBasicBlock) {
                if(((SSACFG.ExceptionHandlerBasicBlock) basicBlock).getCatchInstruction() == null )
                {
                    continue;
                }
                generateDefs(m,ir, ((SSACFG.ExceptionHandlerBasicBlock) basicBlock).getCatchInstruction(), typeInference);
                //session.calcInstructionIndex(((SSACFG.ExceptionHandlerBasicBlock) basicBlock).getCatchInstruction());
                _writer.writeUnsupported(m, ir, ((SSACFG.ExceptionHandlerBasicBlock) basicBlock).getCatchInstruction(), session);
            }

            for (int j = start; j <= end; j++) {
                if (instructions[j] != null) {
                    this.generateDefs(m, ir, instructions[j], typeInference);
                    this.generateUses(m, ir, instructions[j], session, typeInference);

                    if (instructions[j] instanceof SSAReturnInstruction) {
                        generate(m, ir, (SSAReturnInstruction) instructions[j], session, typeInference);
                    }
                    else if (instructions[j] instanceof SSABinaryOpInstruction) {
                        generate(m, ir, (SSABinaryOpInstruction) instructions[j], session, typeInference);
                    }
                    else if (instructions[j] instanceof SSAMonitorInstruction) {
                        generate(m, ir, (SSAMonitorInstruction) instructions[j], session, typeInference);
                    }
                    else if (instructions[j] instanceof SSAThrowInstruction) {
                        generate(m, ir, (SSAThrowInstruction) instructions[j], session, typeInference);
                    }
                    else if (instructions[j] instanceof SSAInvokeInstruction) {
                        generate(m, ir, (SSAInvokeInstruction) instructions[j], session,typeInference);
                    }
                    else if (instructions[j] instanceof SSAGetInstruction) {
                        generate(m, ir, (SSAGetInstruction) instructions[j], session, typeInference);
                    }
                    else if (instructions[j] instanceof SSAPutInstruction) {
                        generate(m, ir, (SSAPutInstruction) instructions[j], session, typeInference);
                    }
                    else if (instructions[j] instanceof SSAUnaryOpInstruction) {
                        generate(m, ir, (SSAUnaryOpInstruction) instructions[j], session, typeInference);
                    }
                    else if (instructions[j] instanceof SSAArrayLengthInstruction) {
                        generate(m, ir, (SSAArrayLengthInstruction) instructions[j], session, typeInference);
                    }
                    else if (instructions[j] instanceof SSAArrayLoadInstruction) {
                        generate(m, ir, (SSAArrayLoadInstruction) instructions[j], session, typeInference);
                    }
                    else if (instructions[j] instanceof SSAArrayStoreInstruction) {
                        generate(m, ir, (SSAArrayStoreInstruction) instructions[j], session, typeInference);
                    }
                    else if (instructions[j] instanceof SSANewInstruction) {
                        generate(m, ir, (SSANewInstruction) instructions[j], session, typeInference);
                    }
                    else if (instructions[j] instanceof SSAComparisonInstruction) {
                        generate(m, ir, (SSAComparisonInstruction) instructions[j], session, typeInference);
                    }
                    else if (instructions[j] instanceof SSALoadMetadataInstruction) {
                        generate(m, ir, (SSALoadMetadataInstruction) instructions[j], session, typeInference);
                    }
                    else if (instructions[j] instanceof SSAInstanceofInstruction) {
                        generate(m, ir, (SSAInstanceofInstruction) instructions[j], session, typeInference);
                    }
                    else if (instructions[j] instanceof SSACheckCastInstruction) {
                        generate(m, ir, (SSACheckCastInstruction) instructions[j], session, typeInference);
                    }
                    else if (instructions[j] instanceof SSAConversionInstruction) {
                        generate(m, ir, (SSAConversionInstruction) instructions[j], session, typeInference);
                    }
                    else if (instructions[j] instanceof SSASwitchInstruction) {
                        //generate(m, ir, (SSASwitchInstruction) instructions[j], session, typeInference);
                        session.calcInstructionNumber(instructions[j]);
                    }
                    else if (instructions[j] instanceof SSAGotoInstruction) {
                        //generate(m, ir, (SSAGotoInstruction) instructions[j], session);
                        session.calcInstructionNumber(instructions[j]);
                    }
                    else if (instructions[j] instanceof SSAConditionalBranchInstruction) {
                        //generate(m, ir, (SSAConditionalBranchInstruction) instructions[j], session, typeInference);
                        session.calcInstructionNumber(instructions[j]);
                    }
                    else{
                        System.out.println("Unknown instruction.");
                    }
                }
            }

            Iterator<SSAPiInstruction> pis = basicBlock.iteratePis();
            while(pis.hasNext())
            {
                SSAPiInstruction piInstruction = pis.next();
            }
        }

        for (int i = 0; i <= cfg.getMaxNumber(); i++) {
            SSACFG.BasicBlock basicBlock = cfg.getNode(i);
            int start = basicBlock.getFirstInstructionIndex();
            int end = basicBlock.getLastInstructionIndex();

            for (int j = start; j <= end; j++) {
                if (instructions[j] instanceof SSASwitchInstruction) {
                    generate(m, ir, (SSASwitchInstruction) instructions[j], session, typeInference);
                } else if (instructions[j] instanceof SSAGotoInstruction) {
                    generate(m, ir, (SSAGotoInstruction) instructions[j], session);
                } else if (instructions[j] instanceof SSAConditionalBranchInstruction) {
                    generate(m, ir, (SSAConditionalBranchInstruction) instructions[j], session, typeInference);
                }
            }
        }

        for (int i = 0; i <= cfg.getMaxNumber(); i++) {
            SSACFG.BasicBlock basicBlock = cfg.getNode(i);
            if (basicBlock instanceof SSACFG.ExceptionHandlerBasicBlock) {
                if(((SSACFG.ExceptionHandlerBasicBlock) basicBlock).getCatchInstruction() == null )
                {
                    continue;
                }_writer.writeExceptionHandler(ir, m ,(SSACFG.ExceptionHandlerBasicBlock)basicBlock,session, typeInference, walaExceptionHelper);
                if (previousHandlerBlock != null) {
                    _writer.writeExceptionHandlerPrevious(m, (SSACFG.ExceptionHandlerBasicBlock) basicBlock, previousHandlerBlock, session);
                }
                previousHandlerBlock = (SSACFG.ExceptionHandlerBasicBlock) basicBlock;
            }
        }

//        int[][] exceArrays = walaExceptionHelper.exceArrays;
//        String[][] exceTypeArrays = walaExceptionHelper.exceTypeArrays;
//
//        for (int i = 0; i <= cfg.getMaxNumber(); i++) {
//            SSACFG.BasicBlock basicBlock = cfg.getNode(i);
//            int start = basicBlock.getFirstInstructionIndex();
//            int end = basicBlock.getLastInstructionIndex();
//
//            for (int j = start; j <= end; j++) {
//                if(instructions[j] != null)
//                {
//                    if(m.getName().toString().equals("initialize") &&
//                            m.getDeclaringClass().getName().toString().equals("Lokhttp3/internal/cache/DiskLruCache")) {
//                        System.out.println(instructions[j].iindex + " " +session.getInstructionIndex(instructions[j]) + " " + session.getMaxInstructionNumber(instructions[j]) + " " + instructions[j].toString(ir.getSymbolTable()));
//                        for (int k = 0; k < exceArrays[j].length ; k++) {
//                            System.out.print(exceArrays[j][k] +" - " + exceTypeArrays[j][k] + ", ");
//                        }
//                        System.out.print("\n");
//                    }
//                }
//            }
//        }
    }

    /* Check if a Type refers to a phantom class */
    private boolean isPhantom(TypeReference t, IClassHierarchy cha) {
        if(t.isPrimitiveType())
            return false;
        if(t.isArrayType())
            return isPhantom(t.getArrayElementType(),cha);
        if(cha.lookupClass(t) == null) {
            _writer.writePhantomType(t);
            return true;
        }
        else
            return false;
    }

    /* Check for phantom classes in a method signature. */
    private boolean isPhantomBased(IMethod m) {
        IClassHierarchy cha = m.getClassHierarchy();
        try {
            TypeReference[] exceptions = m.getDeclaredExceptions();
            if(exceptions!= null)
                for (TypeReference exc: exceptions)
                    if (isPhantom(exc, cha)) {
                        //System.out.println("Exception " + fixTypeString(exc.getName().toString()) + " is phantom.");
                        return true;
                    }
        } catch (InvalidClassFileException e) {
            e.printStackTrace();
        }

        for (int i = 0 ; i < m.getNumberOfParameters(); i++)
            if(isPhantom(m.getParameterType(i), cha)) {
                //System.out.println("Parameter type " + fixTypeString(m.getParameterType(i).toString()) + " of " + m.getSignature() + " is phantom.");
                return true;
            }

        if (isPhantom(m.getReturnType(), cha)) {
            //System.out.println("Return type " + fixTypeString(m.getReturnType().toString()) + " of " + m.getSignature() + " is phantom.");
            return true;
        }

        return false;
    }

    /*
     * From what I understand all SSASwitchInsutrctions are LookUp Switches in WALA
     * This transformation takes place in com.ibm.wala.shrikeBT.Decoder.java
     * In constrast soot has both LookUp Switches and Table Switches
     */
    private void generate(IMethod m, IR ir, SSASwitchInstruction instruction, Session session, TypeInference typeInference) {
        //Switch instructions have only one use
        Local switchVar = createLocal(ir,instruction,instruction.getUse(0),typeInference);
        _writer.writeLookupSwitch(ir, m, instruction, session, switchVar);

    }
    private void generate(IMethod m, IR ir, SSAConditionalBranchInstruction instruction, Session session, TypeInference typeInference) {
        SSAInstruction[] ssaInstructions = ir.getInstructions();
        SSAInstruction targetInstr;
        // Conditional branch instructions have two uses (op1 and op2, the compared variables) and no defs
        Local op1 = createLocal(ir, instruction, instruction.getUse(0), typeInference);
        Local op2 = createLocal(ir, instruction, instruction.getUse(1), typeInference);

        int brachTarget = instruction.getTarget();

        if(m instanceof DexIMethod) {
            IBytecodeMethod<?> bm = (IBytecodeMethod<?>)m;
            try {
                brachTarget = bm.getInstructionIndex(brachTarget);
            } catch (InvalidClassFileException e) {
                e.printStackTrace();
            }
        }

        if(brachTarget == -1) //In Android conditional branches can have -1 as target
            brachTarget =0;
        if(ssaInstructions[brachTarget] == null) {
            targetInstr = getNextNonNullInstruction(ir,brachTarget);
            if(targetInstr == null)
                logger.error("Error: Next non-null instruction index = -1");
        }
        else
            targetInstr = ssaInstructions[brachTarget];
        _writer.writeIf(m, instruction, op1, op2, targetInstr, session);
    }


    private void generate(IMethod m, IR ir, SSAPhiInstruction instruction, Session session, TypeInference typeInference) {
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

    private void generate(IMethod m, IR ir, SSANewInstruction instruction, Session session, TypeInference typeInference) {
        Local l = createLocal(ir,instruction,instruction.getDef(),typeInference);
        int numOfUses = instruction.getNumberOfUses();
        if(numOfUses < 2)
        {
            _writer.writeAssignHeapAllocation(ir, m, instruction, l, session);
        }
        else
        {
            _writer.writeAssignNewMultiArrayExpr(ir, m, instruction, l, session);
        }
    }

    private void generate(IMethod m, IR ir, SSALoadMetadataInstruction instruction, Session session, TypeInference typeInference) {
        Local l =  createLocal(ir,instruction,instruction.getDef(),typeInference);
        ConstantValue v = new ConstantValue(instruction.getToken());
        _writer.writeClassConstantExpression(m, instruction, l, v, session);
    }

    private void generate(IMethod m, IR ir, SSAArrayLoadInstruction instruction, Session session, TypeInference typeInference) {
        // Load array instructions have a single def (to) and two uses (base and index);
        Local to = createLocal(ir, instruction, instruction.getDef(), typeInference);
        Local base = createLocal(ir, instruction, instruction.getUse(0), typeInference);
        Local index = createLocal(ir, instruction, instruction.getUse(1), typeInference);

        _writer.writeLoadArrayIndex(m, instruction, base, to, index, session);
    }

    private void generate(IMethod m, IR ir, SSAArrayStoreInstruction instruction, Session session, TypeInference typeInference) {
        // Store arra instructions have three uses (base, index and from) and no defs
        Local base = createLocal(ir, instruction, instruction.getUse(0), typeInference);
        Local index = createLocal(ir, instruction, instruction.getUse(1), typeInference);
        Local from = createLocal(ir, instruction, instruction.getUse(2), typeInference);

        _writer.writeStoreArrayIndex(m, instruction, base, from, index, session);
    }

    private void generate(IMethod m, IR ir, SSAGetInstruction instruction, Session session, TypeInference typeInference) {
        Local to = createLocal(ir, instruction, instruction.getDef(), typeInference);

        if (instruction.isStatic()) {
            //Get static field has no uses and a single def (to)
            _writer.writeLoadStaticField(m, instruction, instruction.getDeclaredField(), to, session);
        }
        else {
            //Get instance field has one use (base) and one def (to)
            Local base = createLocal(ir, instruction, instruction.getUse(0), typeInference);
            _writer.writeLoadInstanceField(m, instruction, instruction.getDeclaredField(), base, to, session);
        }
    }

    private void generate(IMethod m, IR ir, SSAPutInstruction instruction, Session session, TypeInference typeInference) {

        if (instruction.isStatic()) {
            //Put static field has a single use (from) and no defs
            Local from = createLocal(ir, instruction, instruction.getUse(0), typeInference);
            _writer.writeStoreStaticField(m, instruction, instruction.getDeclaredField(), from, session);
        }
        else {
            //Put instance field has two uses (base and from) and no defs
            Local base = createLocal(ir, instruction, instruction.getUse(0), typeInference);
            Local from = createLocal(ir, instruction, instruction.getUse(1), typeInference);
            _writer.writeStoreInstanceField(m, instruction, instruction.getDeclaredField(), base, from, session);
        }
    }

    private void generate(IMethod m, IR ir, SSAInvokeInstruction instruction, Session session, TypeInference typeInference) {
        // For invoke instructions the number of uses is equal to the number of parameters

        Local l;
        if(instruction.getNumberOfReturnValues() == 0)
            l = null;
        else
            l = createLocal(ir, instruction, instruction.getDef(), typeInference);
        _writer.writeInvoke(m, ir, instruction, l, session,typeInference);
    }

    private void generate(IMethod m, IR ir, SSAInstanceofInstruction instruction, Session session, TypeInference typeInference) {
        // For invoke instructions the number of uses is equal to the number of parameters
        Local to = createLocal(ir, instruction, instruction.getDef(), typeInference);
        Local from = createLocal(ir, instruction, instruction.getUse(0), typeInference);
        _writer.writeAssignInstanceOf(m,  instruction, to, from,instruction.getCheckedType(), session);
    }

    //SSACheckCastInstruction is for non primitive types
    private void generate(IMethod m, IR ir, SSACheckCastInstruction instruction, Session session, TypeInference typeInference) {
        // For invoke instructions the number of uses is equal to the number of parameters
        Local to =createLocal(ir, instruction, instruction.getDef(), typeInference);
        Local from = createLocal(ir, instruction, instruction.getUse(0), typeInference);
        TypeReference[] types = instruction.getDeclaredResultTypes();
        if(types.length!=1)
        {
            logger.debug("Instruction: " + instruction.toString(ir.getSymbolTable()));
            for(TypeReference type:types)
                logger.debug("Checkcast type is " + type.toString());
        }
        for(TypeReference type:types) {
            if(ir.getSymbolTable().isStringConstant(instruction.getUse(0)) || ir.getSymbolTable().isNullConstant(instruction.getUse(0)))//TODO:No class constant?
                _writer.writeAssignCastNull(m,instruction,to,type,session);
            else
                _writer.writeAssignCast(m, instruction, to, from, type, session);
        }
    }

    //SSAConversion Instruction is only for primitive types
    private void generate(IMethod m, IR ir, SSAConversionInstruction instruction, Session session, TypeInference typeInference) {
        // For invoke instructions the number of uses is equal to the number of parameters
        Local to =createLocal(ir, instruction, instruction.getDef(), typeInference);
        Local from = createLocal(ir, instruction, instruction.getUse(0), typeInference);

        if(ir.getSymbolTable().isNumberConstant(instruction.getUse(0)) )
            _writer.writeAssignCastNumericConstant(m, instruction, to, from , instruction.getToType(), session);
        else
            _writer.writeAssignCast(m, instruction, to, from , instruction.getToType(), session);
    }

    private void generate(IMethod m, IR ir, SSAGotoInstruction instruction, Session session) {
        // Go to instructions have no uses and no defs
        SSAInstruction[] ssaInstructions = ir.getInstructions();
        SSAInstruction targetInstr;
        int gotoTarget = instruction.getTarget();
//        if(m instanceof DexIMethod) {
//            IBytecodeMethod bm = (IBytecodeMethod)m;
//            try {
//                gotoTarget = bm.getInstructionIndex(gotoTarget);
//            } catch (InvalidClassFileException e) {
//                e.printStackTrace();
//            }
//        }
        if(gotoTarget < 0) //In Android conditional GoTos can have -1 as target
        {
            //System.out.println("goto " + instruction.getTarget() + " for instr " + instruction.toString());
            gotoTarget = 0;
        }

        if(ssaInstructions[gotoTarget] == null) {
            targetInstr = getNextNonNullInstruction(ir,gotoTarget);
            if(targetInstr == null)
                System.out.println("Error: Next non-null instruction index = -1");
        }
        else
            targetInstr = ssaInstructions[gotoTarget];
        _writer.writeGoto(m, instruction,targetInstr , session);
    }

    private void generate(IMethod m, IR ir, SSAMonitorInstruction instruction, Session session, TypeInference typeInference) {
        // Monitor instructions have a single use and no defs
        int use = instruction.getUse(0);
        Local l = createLocal(ir, instruction, use, typeInference);
        if (instruction.isMonitorEnter()) {
            _writer.writeEnterMonitor(m, instruction, l, session);
        }
        else {
            _writer.writeExitMonitor(m, instruction, l, session);
        }
    }

    private void generate(IMethod m, IR ir, SSAUnaryOpInstruction instruction, Session session, TypeInference typeInference) {
        // Unary op instructions have a single def (to) and a single use (from)
        Local to = createLocal(ir, instruction, instruction.getDef(), typeInference);
        Local from = createLocal(ir, instruction, instruction.getUse(0), typeInference);

        _writer.writeAssignUnop(m, instruction, to, from, session);
    }

    private void generate(IMethod m, IR ir, SSAArrayLengthInstruction instruction, Session session, TypeInference typeInference) {
        // Array length instruction have a single use (base) and a def (to)
        Local to = createLocal(ir, instruction, instruction.getDef(), typeInference);
        Local base = createLocal(ir, instruction, instruction.getUse(0), typeInference);

        _writer.writeAssignArrayLength(m, instruction, to, base, session);
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
                Local l = createLocal(ir, instruction, def, typeInference);
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
            if(instruction instanceof  SSAPhiInstruction && use < 0) //For some reason phi instructions can have -1 as use
                continue;
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
            l.setType(TypeReference.JavaLangString);
            _writer.writeStringConstantExpression(ir, m, instruction, l, (ConstantValue) v, session);
        } else if (v.isNullConstant()) {
            _writer.writeNullExpression(m, instruction, l, session);
        } else if (symbolTable.isIntegerConstant(l.getVarIndex())) {
            l.setType(TypeReference.Int);
            _writer.writeNumConstantExpression(m, instruction, l, (ConstantValue) v, session);
        } else if (symbolTable.isLongConstant(l.getVarIndex())) {
            l.setType(TypeReference.Long);
            _writer.writeNumConstantExpression(m, instruction, l, (ConstantValue) v, session);
        } else if (symbolTable.isFloatConstant(l.getVarIndex())) {
            l.setType(TypeReference.Float);
            _writer.writeNumConstantExpression(m, instruction, l, (ConstantValue) v, session);
        } else if (symbolTable.isDoubleConstant(l.getVarIndex())) {
            l.setType(TypeReference.Double);
            _writer.writeNumConstantExpression(m, instruction, l, (ConstantValue) v, session);
        } else if (symbolTable.isBooleanConstant(l.getVarIndex())) {
            l.setType(TypeReference.Boolean);
            _writer.writeNumConstantExpression(m, instruction, l, (ConstantValue) v, session);
        } else if (s.startsWith("#[") || (s.startsWith("#L") && s.endsWith(";"))) {
            _writer.writeClassConstantExpression(m, instruction, l, (ConstantValue) v, session);
        }
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
}
