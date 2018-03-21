package org.clyze.doop.wala;

import com.ibm.wala.analysis.typeInference.TypeInference;
import com.ibm.wala.classLoader.*;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.IAnalysisCacheView;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.ssa.*;
import com.ibm.wala.ssa.Value;
import com.ibm.wala.types.TypeReference;
import soot.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Iterator;

/**
 * Traverses Soot classes and invokes methods in FactWriter to
 * generate facts. The class FactGenerator is the main class
 * controlling what facts are generated.
 */

class WalaFactGenerator {

    private WalaFactWriter _writer;
    private Iterator<IClass> _iClasses;
    private AnalysisOptions options;
    private IAnalysisCacheView cache;
    private WalaIRPrinter IRPrinter;

    WalaFactGenerator(WalaFactWriter writer, Iterator<IClass> iClasses)
    {
        this._writer = writer;

        this._iClasses = iClasses;
        options = new AnalysisOptions();
        options.getSSAOptions().setPiNodePolicy(SSAOptions.getAllBuiltInPiNodes());
        cache = new AnalysisCacheImpl();
        IRPrinter = new WalaIRPrinter(cache);
    }


    public void run() {

        int skipped=0,overall=0;
        while (_iClasses.hasNext()) {
            IClass iClass = _iClasses.next();
            overall++;
            if(iClass.getClassLoader().getName().toString().equals("Primordial")) { //Skipping classes using the Primordial classloader for now to produce less facts
                skipped++;
                continue;
            }
            //System.out.println("Class " + iClass.getName().toString() + " loader " + iClass.getClassLoader().getName().toString() + " skipped " + skipped + " from " + overall);
            //IRPrinter.printIR(iClass);
            _writer.writeClassOrInterfaceType(iClass);

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

            iClass.getAllFields().forEach(this::generate);


            for (IMethod m : iClass.getAllMethods()) {
                Session session = new org.clyze.doop.wala.Session();
                generate(m, session);
            }
        }
        System.out.println("Skipped " + skipped + " from " + overall);
    }

    private void generate(IField f)
    {
        _writer.writeField(f);
        _writer.writeFieldInitialValue(f);


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


    /* Check if a Type refers to a phantom class */
    private static boolean phantomBased(Type t) {
        if (t instanceof RefLikeType) {
            if (t instanceof RefType)
                return ((RefType) t).getSootClass().isPhantom();
            else if (t instanceof ArrayType)
                return phantomBased(((ArrayType) t).getElementType());
        }
        return false;
    }

    public static boolean phantomBased(IMethod m) {

        /* Check for phantom classes */

//        if (m.isPhantom()) {
//            System.out.println("Method " + m.getSignature() + " is phantom.");
//            return true;
//        }
//
//        for(SootClass clazz: m.getExceptions())
//            if (clazz.isPhantom()) {
//                System.out.println("Class " + clazz.getName() + " is phantom.");
//                return true;
//            }
//
//        for(int i = 0 ; i < m.getParameterCount(); i++)
//            if(phantomBased(m.getParameterType(i))) {
//                System.out.println("Parameter type " + m.getParameterType(i) + " of " + m.getSignature() + " is phantom.");
//                return true;
//            }
//
//        if (phantomBased(m.getReturnType())) {
//            System.out.println("Return type " + m.getReturnType() + " of " + m.getSignature() + " is phantom.");
//            return true;
//        }

        return false;
    }

    private void generate(IMethod m, Session session)
    {
        if (phantomBased(m)) {
            //m.setPhantom(true);
            return;
        }

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
        // TODO would be nice to have isVarArgs in Soot
//        if(Modifier.isTransient(modifiers))
//            _writer.writeMethodModifier(m, "varargs");
        // TODO would be nice to have isBridge in Soot
        if(m.isSynchronized())
            _writer.writeMethodModifier(m, "volatile");
        if(m.isSynthetic())
            _writer.writeMethodModifier(m, "synthetic");
        if(m.isBridge())
            _writer.writeMethodModifier(m, "bridge");
        // TODO interface?
        // TODO strictfp?
        // TODO annotation?
        // TODO enum?

        if(!m.isStatic())
        {
            _writer.writeThisVar(m);
        }

        if(m.isNative())
        {
            _writer.writeNativeReturnVar(m);
        }

        for(int i = 0 ; i < m.getNumberOfParameters(); i++)
        {
            _writer.writeFormalParam(m, i);
        }

        try {
            for(TypeReference exceptionType: m.getDeclaredExceptions())
            {
                _writer.writeMethodDeclaresException(m, exceptionType);
            }
        } catch (InvalidClassFileException e) {
            e.printStackTrace();
        }

        if(!(m.isAbstract() || m.isNative()))
        {
            IR ir = cache.getIR(m, Everywhere.EVERYWHERE);
            generate(m, ir, session);
        }
    }

    private void generate(IMethod m, IR ir, Session session)
    {

        SSAInstruction[] instructions = ir.getInstructions();
        SSACFG cfg = ir.getControlFlowGraph();
        TypeInference typeInference = TypeInference.make(ir,true); // Not sure about true for doPrimitives
        for (int i = 0; i <=cfg.getMaxNumber(); i++) {
            SSACFG.BasicBlock basicBlock = cfg.getNode(i);
            int start = basicBlock.getFirstInstructionIndex();
            int end = basicBlock.getLastInstructionIndex();

            for (int j = start; j <= end; j++) {
                if (instructions[j] != null) {
                    this.generateDefs(m, ir, instructions[j], session, typeInference);
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
                    else if (instructions[j] instanceof SSAGotoInstruction) {
                        generate(m, ir, (SSAGotoInstruction) instructions[j], session);
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
                    else if (instructions[j] instanceof SSASwitchInstruction) {

                    }
                    else if (instructions[j] instanceof SSAConditionalBranchInstruction) {
                        generate(m, ir, (SSAConditionalBranchInstruction) instructions[j], session, typeInference);
                    }
                }
            }
        }
    }

    public void generate(IMethod m, IR ir, SSAConditionalBranchInstruction instruction, Session session, TypeInference typeInference) {
        SSAInstruction[] ssaInstructions = ir.getInstructions();

        // Conditional branch instructions have two uses (op1 and op2, the compared variables) and no defs
        Local op1 = createLocal(ir, instruction, instruction.getUse(0), typeInference);
        Local op2 = createLocal(ir, instruction, instruction.getUse(1), typeInference);

        _writer.writeIf(m, instruction, op1, op2, ssaInstructions[instruction.getTarget()], session);
    }

    public void generate(IMethod m, IR ir, SSAArrayLoadInstruction instruction, Session session, TypeInference typeInference) {
        // Load array instructions have a single def (to) and two uses (base and index);
        Local to = createLocal(ir, instruction, instruction.getDef(), typeInference);
        Local base = createLocal(ir, instruction, instruction.getUse(0), typeInference);
        Local index = createLocal(ir, instruction, instruction.getUse(1), typeInference);

        _writer.writeLoadArrayIndex(m, instruction, base, to, index, session);
    }

    public void generate(IMethod m, IR ir, SSAArrayStoreInstruction instruction, Session session, TypeInference typeInference) {
        // Store arra instructions have three uses (base, index and from) and no defs
        Local base = createLocal(ir, instruction, instruction.getUse(0), typeInference);
        Local index = createLocal(ir, instruction, instruction.getUse(1), typeInference);
        Local from = createLocal(ir, instruction, instruction.getUse(2), typeInference);

        _writer.writeStoreArrayIndex(m, instruction, base, from, index, session);
    }

    public void generate(IMethod m, IR ir, SSAGetInstruction instruction, Session session, TypeInference typeInference) {
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

    public void generate(IMethod m, IR ir, SSAPutInstruction instruction, Session session, TypeInference typeInference) {

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

    public void generate(IMethod m, IR ir, SSAInvokeInstruction instruction, Session session, TypeInference typeInference) {
        // For invoke instructions the number of uses is equal to the number of parameters
        _writer.writeInvoke(m, ir, instruction, session,typeInference);
    }


    public void generate(IMethod m, IR ir, SSAGotoInstruction instruction, Session session) {
        // Go to instructions have no uses and no defs
        SSAInstruction[] ssaInstructions = ir.getInstructions();
        _writer.writeGoto(m, instruction,ssaInstructions[instruction.getTarget()] , session);
    }

    public void generate(IMethod m, IR ir, SSAMonitorInstruction instruction, Session session, TypeInference typeInference) {
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

    public void generate(IMethod m, IR ir, SSAUnaryOpInstruction instruction, Session session, TypeInference typeInference) {
        // Unary op instructions have a single def (to) and a single use (from)
        Local to = createLocal(ir, instruction, instruction.getDef(), typeInference);
        Local from = createLocal(ir, instruction, instruction.getUse(0), typeInference);

        _writer.writeAssignUnop(m, instruction, to, from, session);
    }

    public void generate(IMethod m, IR ir, SSAArrayLengthInstruction instruction, Session session, TypeInference typeInference) {
        // Array length instruction have a single use (base) and a def (to)
        Local to = createLocal(ir, instruction, instruction.getDef(), typeInference);
        Local base = createLocal(ir, instruction, instruction.getUse(0), typeInference);

        _writer.writeAssignArrayLength(m, instruction, to, base, session);
    }

    private Local createLocal(IR ir, SSAInstruction instruction, int varIndex, TypeInference typeInference) {
        Local l;
        String[] localNames = ir.getLocalNames(instruction.iindex, varIndex);
        TypeReference typeRef;
        if(typeInference.getType(varIndex).getType() == null)
            typeRef = TypeReference.JavaLangObject;
        else
            typeRef = typeInference.getType(varIndex).getTypeReference();
        
        if (localNames != null) {
            assert localNames.length == 1;
            l = new Local("v" + varIndex, varIndex, localNames[0], typeRef);
        }
        else {
            l = new Local("v" + varIndex, varIndex, typeRef);
        }
        return l;
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
//            int returnVar = instruction.getUse(0);
//            String[] localNames = ir.getLocalNames(instruction.iindex, returnVar);
//            if (localNames != null) {
//                assert localNames.length == 1;
//                l = new Local("v" + returnVar, localNames[0], m.getReturnType());
//            }
//            //TODO : Check when this occurs
//            else {
//
//                l = new Local("v" + returnVar, m.getReturnType());
//            }
            _writer.writeReturn(m, instruction, l, session);
        }
    }

    private void generateDefs(IMethod m, IR ir, SSAInstruction instruction, Session session, TypeInference typeInference) {
        SymbolTable symbolTable = ir.getSymbolTable();

        if (instruction.hasDef()) {
            for (int i = 0; i < instruction.getNumberOfDefs(); i++) {
                int def = instruction.getDef(i);
                Local l = createLocal(ir, instruction, def, typeInference);
                if (def != 1 && symbolTable.isConstant(def)) {
                    Value v = symbolTable.getValue(def);
                    generateConstant(m, ir, instruction, v, l, session);
                } else {
                    _writer.writeLocal(m, l);
                }
            }
        }
    }

    private void generateUses(IMethod m, IR ir, SSAInstruction instruction, Session session, TypeInference typeInference) {
        SymbolTable symbolTable = ir.getSymbolTable();

        for (int i = 0; i < instruction.getNumberOfUses(); i++) {
            int use = instruction.getUse(i);
            Local l = createLocal(ir, instruction, use, typeInference);
            if (use != -1 && symbolTable.isConstant(use)) {
                Value v = symbolTable.getValue(use);
                generateConstant(m, ir, instruction, v, l, session);
            }
            else {
                _writer.writeLocal(m, l);
            }
        }
    }

    private void generateConstant(IMethod m, IR ir, SSAInstruction instruction, Value v, Local l, Session session) {
        SymbolTable symbolTable = ir.getSymbolTable();

        String s = v.toString();
        if (v.isStringConstant()) {
            _writer.writeStringConstantExpression(m, instruction, l, (ConstantValue) v, session);
        } else if (v.isNullConstant()) {
            _writer.writeNullExpression(m, instruction, l, session);
        } else if (symbolTable.isIntegerConstant(l.getVarIndex())) {
            _writer.writeNumConstantExpression(m, instruction, l, (ConstantValue) v, session);
        } else if (symbolTable.isLongConstant(l.getVarIndex())) {
            _writer.writeNumConstantExpression(m, instruction, l, (ConstantValue) v, session);
        } else if (symbolTable.isFloatConstant(l.getVarIndex())) {
            _writer.writeNumConstantExpression(m, instruction, l, (ConstantValue) v, session);
        } else if (symbolTable.isIntegerConstant(l.getVarIndex())) {
            _writer.writeNumConstantExpression(m, instruction, l, (ConstantValue) v, session);
        } else if (symbolTable.isDoubleConstant(l.getVarIndex())) {
            _writer.writeNumConstantExpression(m, instruction, l, (ConstantValue) v, session);
        } else if (symbolTable.isBooleanConstant(l.getVarIndex())) {
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

    private void generate(IMethod inMethod, IR ir, SSAThrowInstruction instruction, Session session, TypeInference typeInference)
    {
        // Throw instructions have a single use and no defs
        SymbolTable symbolTable = ir.getSymbolTable();
        int use = instruction.getUse(0);

        if(!symbolTable.isConstant(use))
        {
            Local l = createLocal(ir, instruction, use, typeInference);

            _writer.writeThrow(inMethod, instruction, l, session);
        }
        else if(symbolTable.isNullConstant(use))
        {
            _writer.writeThrowNull(inMethod, instruction, session);
        }
        else
        {
            throw new RuntimeException("Unhandled throw statement: " + instruction);
        }
    }
}

