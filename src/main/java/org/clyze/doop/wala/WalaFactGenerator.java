package org.clyze.doop.wala;

import com.ibm.wala.classLoader.*;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.IAnalysisCacheView;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.ssa.*;
import com.ibm.wala.types.TypeReference;
import org.clyze.doop.soot.Session;
import soot.*;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.IdentityStmt;
import soot.jimple.ThrowStmt;
import soot.util.EscapedWriter;

import java.io.*;
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

    WalaFactGenerator(WalaFactWriter writer, Iterator<IClass> iClasses)
    {
        this._writer = writer;

        this._iClasses = iClasses;
        options = new AnalysisOptions();
        options.getSSAOptions().setPiNodePolicy(SSAOptions.getAllBuiltInPiNodes());
        cache = new AnalysisCacheImpl();
    }


    public void run() {

        while (_iClasses.hasNext()) {
            IClass iClass = _iClasses.next();
            printIR(iClass);
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
                generate(m);
            }


        }
    }
    public String fixTypeString(String original)
    {
        boolean isArrayType = false;
        if(original.contains("[L")) //Figure out if this is correct
            isArrayType = true;
        String ret = original.substring(original.indexOf("L") +1).replaceAll("/",".").replaceAll(">","");
        String temp;
        if(ret.contains("Primordial"))
        {
            temp = ret.substring(ret.indexOf(",") + 1);
            if(temp.startsWith("["))
            {
                isArrayType = true;
                temp = temp.substring(1);
            }
            if(temp.equals("Z"))
                 ret = "boolean";
            else if(temp.equals("I"))
                ret = "int";
            else if(temp.equals("V"))
                ret = "void";
            else if(temp.equals("B"))
                ret = "byte";
            else if(temp.equals("C"))
                ret = "char";
            else if(temp.equals("D"))
                ret = "double";
            else if(temp.equals("F"))
                ret = "float";
            else if(temp.equals("J"))
                ret = "long";
            else if(temp.equals("S"))
                ret = "short";
            //TODO: Figure out what the 'P' code represents in WALA's TypeRefference
        }
        if(isArrayType)
            ret = ret + "[]";
        return ret;
    }

    public void printMemberAttributes(IMember member)
    {

    }

    public void printIR(IClass cl)
    {
//        PrintWriter writerOut = new PrintWriter(new EscapedWriter(new OutputStreamWriter((OutputStream)streamOut)));
        ShrikeClass shrikeClass = (ShrikeClass) cl;
        String fileName = "WalaFacts/IR/" + cl.getReference().getName().toString().replaceAll("/",".").replaceFirst("L","");
        File file = new File(fileName);
        file.getParentFile().getParentFile().mkdirs();
        file.getParentFile().mkdirs();

        Collection<IField> fields = cl.getAllFields();
        Collection<IMethod> methods = cl.getDeclaredMethods();
        Collection<IClass> interfaces =  cl.getAllImplementedInterfaces();
        try {
            PrintWriter printWriter = new PrintWriter(file);
            if(shrikeClass.isPublic())
                printWriter.write("public ");
            else if(shrikeClass.isPrivate())
                printWriter.write("private ");

            if(shrikeClass.isAbstract())
                printWriter.write("abstract ");

            if(shrikeClass.isInterface())
                printWriter.write("interface ");
            else
                printWriter.write("class ");

            printWriter.write(cl.getReference().getName().toString().replaceAll("/",".").replaceFirst("L",""));

            printWriter.write("\n{\n");
            for(IField field : fields )
            {
                printWriter.write("\t");
                if(field.isPublic())
                    printWriter.write("public ");
                else if(field.isPrivate())
                    printWriter.write("private ");
                else if(field.isProtected())
                    printWriter.write("protected ");
                if(field.isStatic())
                    printWriter.write("static ");
                printWriter.write(fixTypeString(field.getFieldTypeReference().toString()) + " " + field.getName() + ";\n");
                //printWriter.write("\t" + field.getFieldTypeReference().toString() + " " + field.getReference().getSignature() + "\n");
                //printWriter.write("\t" + field.getFieldTypeReference().toString() + " " + field.getReference().toString() + "\n");
            }
            for (IMethod m : methods)
            {
                printWriter.write("\n\t");
                if(m.isPublic())
                    printWriter.write("public ");
                else if(m.isPrivate())
                    printWriter.write("private ");
                else if(m.isProtected())
                    printWriter.write("protected ");

                if(m.isStatic())
                    printWriter.write("static ");

                if(m.isFinal())
                    printWriter.write("final ");

                if(m.isAbstract())
                    printWriter.write("abstract ");

                if(m.isSynchronized())
                    printWriter.write("synchronized ");

                printWriter.write(fixTypeString(m.getReturnType().toString()) + " " + m.getReference().getName().toString() + "(");
                for (int i = 0; i < m.getNumberOfParameters(); i++) {
                    printWriter.write(fixTypeString(m.getParameterType(i).toString()) + " ");
                    if (i < m.getNumberOfParameters() - 1)
                        printWriter.write(", ");
                }
                printWriter.write(")\n\t{\n");
                if(!(m.isAbstract() || m.isNative()))
                {
                    printIR(m,printWriter);
                }
                printWriter.write("\t}\n");

            }

            printWriter.write("}\n");
            printWriter.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }
    public void printIR(IMethod m,PrintWriter writer)
    {
        IR ir = cache.getIR(m, Everywhere.EVERYWHERE);
        SSAInstruction[] instructions = ir.getInstructions();
        SSACFG cfg = ir.getControlFlowGraph();
        for (int i = 0; i <=cfg.getMaxNumber(); i++) {
            writer.write("\t\tBB "+ i + "\n");
            SSACFG.BasicBlock basicBlock = cfg.getNode(i);
            int start = basicBlock.getFirstInstructionIndex();
            int end = basicBlock.getLastInstructionIndex();

            for (int j = start; j <= end; j++) {
                if (instructions[j] != null) {
                    writer.write("\t\t\t" + instructions[j].toString() + "\n");

                }
            }
        }
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

    void generate(IMethod m)
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
            generate(m, ir);
        }
    }

    private void generate(IMethod m, IR ir)
    {

        SSAInstruction[] instructions = ir.getInstructions();
        SSACFG cfg = ir.getControlFlowGraph();
        for (int i = 0; i <=cfg.getMaxNumber(); i++) {
            SSACFG.BasicBlock basicBlock = cfg.getNode(i);
            int start = basicBlock.getFirstInstructionIndex();
            int end = basicBlock.getLastInstructionIndex();

            for (int j = start; j <= end; j++) {
                if (instructions[j] != null) {
                    if (instructions[j] instanceof SSAReturnInstruction) {
                        generate(m, ir, (SSAReturnInstruction) instructions[j]);
                    }
                }
            }
        }
//        for(Local l : b.getLocals())
//        {
//            _writer.writeLocal(m, l);
//        }
//
//        IrrelevantStmtSwitch sw =  new IrrelevantStmtSwitch();
//        for(Unit u : b.getUnits())
//        {
//            Stmt stmt = (Stmt) u;
//
//            stmt.apply(sw);
//
//            if(sw.relevant)
//            {
//                if(stmt instanceof AssignStmt)
//                {
//                    generate(m, (AssignStmt) stmt, session);
//                }
//                else if(stmt instanceof IdentityStmt)
//                {
//                    generate(m, (IdentityStmt) stmt, session);
//                }
//                else if(stmt instanceof InvokeStmt)
//                {
//                    _writer.writeInvoke(m, stmt, stmt.getInvokeExpr(), session);
//                }
//                else if(stmt instanceof ReturnStmt)
//                {
//                    generate(m, (ReturnStmt) stmt, session);
//                }
//                else if(stmt instanceof ReturnVoidStmt)
//                {
//                    _writer.writeReturnVoid(m, stmt, session);
//                }
//                else if(stmt instanceof ThrowStmt)
//                {
//                    generate(m, (ThrowStmt) stmt, session);
//                }
//                else if(stmt instanceof GotoStmt)
//                {
//                    // processed in second run: we might not know the number of
//                    // the unit yet.
//                    session.calcUnitNumber(stmt);
//                }
//                else if(stmt instanceof IfStmt)
//                {
//                    // processed in second run: we might not know the number of
//                    // the unit yet.
//                    session.calcUnitNumber(stmt);
//                }
//                else if(stmt instanceof EnterMonitorStmt)
//                {
//                    //TODO: how to handle EnterMonitorStmt when op is not a Local?
//                    if (((EnterMonitorStmt) stmt).getOp() instanceof Local)
//                        _writer.writeEnterMonitor(m, stmt, (Local) ((EnterMonitorStmt) stmt).getOp(), session);
//                }
//                else if(stmt instanceof ExitMonitorStmt)
//                {
//                    //TODO: how to handle ExitMonitorStmt when op is not a Local?
//                    if (((ExitMonitorStmt) stmt).getOp() instanceof Local)
//                        _writer.writeExitMonitor(m, stmt, (Local) ((ExitMonitorStmt) stmt).getOp(), session);
//                }
//                else if(stmt instanceof TableSwitchStmt)
//                {
//                    // same as IfStmt and GotoStmt.
//                    session.calcUnitNumber(stmt);
//                }
//                else if(stmt instanceof LookupSwitchStmt)
//                {
//                    session.calcUnitNumber(stmt);
//                }
//                else if (stmt instanceof NopStmt) {
//                    session.calcUnitNumber(stmt);
//                }
//                else
//                {
//                    throw new RuntimeException("Cannot handle statement: " + stmt);
//                }
//            }
//            else
//            {
//                // only reason for assign or invoke statements to be irrelevant
//                // is the invocation of a method on a phantom class
//                if(stmt instanceof AssignStmt)
//                    _writer.writeAssignPhantomInvoke(m, stmt, session);
//                else if (stmt instanceof InvokeStmt)
//                    _writer.writePhantomInvoke(m, stmt, session);
//                else if (stmt instanceof BreakpointStmt)
//                    _writer.writeBreakpointStmt(m, stmt, session);
//                else
//                    throw new RuntimeException("Unexpected irrelevant statement: " + stmt);
//            }
//        }
//
//        for(Unit u : b.getUnits())
//        {
//            Stmt stmt = (Stmt) u;
//
//            if(stmt instanceof GotoStmt)
//            {
//                _writer.writeGoto(m, stmt, ((GotoStmt) stmt).getTarget(), session);
//            }
//            else if(stmt instanceof IfStmt)
//            {
//                _writer.writeIf(m, stmt, ((IfStmt) stmt).getTarget(), session);
//            }
//            else if(stmt instanceof TableSwitchStmt)
//            {
//                _writer.writeTableSwitch(m, (TableSwitchStmt) stmt, session);
//            }
//            else if(stmt instanceof LookupSwitchStmt)
//            {
//                _writer.writeLookupSwitch(m, (LookupSwitchStmt) stmt, session);
//            }
//        }
//
//        Trap previous = null;
//        for(Trap t : b.getTraps())
//        {
//            _writer.writeExceptionHandler(m, t, session);
//            if(previous != null)
//            {
//                _writer.writeExceptionHandlerPrevious(m, t, previous, session);
//            }
//
//            previous = t;
//        }
    }

    /**
     * Assignment statement
     */
    public void generate(IMethod inMethod, AssignStmt stmt, Session session)
    {
        Value left = stmt.getLeftOp();

        if(left instanceof Local)
        {
            generateLeftLocal(inMethod, stmt, session);
        }
        else
        {
            generateLeftNonLocal(inMethod, stmt, session);
        }
    }

    private void generateLeftLocal(IMethod inMethod, AssignStmt stmt, Session session)
    {
//        Local left = (Local) stmt.getLeftOp();
//        Value right = stmt.getRightOp();
//
//        if(right instanceof Local)
//        {
//            _writer.writeAssignLocal(inMethod, stmt, left, (Local) right, session);
//        }
//        else if(right instanceof InvokeExpr)
//        {
//            _writer.writeAssignInvoke(inMethod, stmt, left, (InvokeExpr) right, session);
//        }
//        else if(right instanceof NewExpr)
//        {
//            _writer.writeAssignHeapAllocation(inMethod, stmt, left, (NewExpr) right, session);
//        }
//        else if(right instanceof NewArrayExpr)
//        {
//            _writer.writeAssignHeapAllocation(inMethod, stmt, left, (NewArrayExpr) right, session);
//        }
//        else if(right instanceof NewMultiArrayExpr)
//        {
//            _writer.writeAssignNewMultiArrayExpr(inMethod, stmt, left, (NewMultiArrayExpr) right, session);
//        }
//        else if(right instanceof StringConstant)
//        {
//            _writer.writeAssignStringConstant(inMethod, stmt, left, (StringConstant) right, session);
//        }
//        else if(right instanceof ClassConstant)
//        {
//            _writer.writeAssignClassConstant(inMethod, stmt, left, (ClassConstant) right, session);
//        }
//        else if(right instanceof NumericConstant)
//        {
//            _writer.writeAssignNumConstant(inMethod, stmt, left, (NumericConstant) right, session);
//        }
//        else if(right instanceof NullConstant)
//        {
//            _writer.writeAssignNull(inMethod, stmt, left, session);
//            // NoNullSupport: use the line below to remove Null Constants from the facts.
//            // _writer.writeUnsupported(inMethod, stmt, session);
//        }
//        else if(right instanceof InstanceFieldRef)
//        {
//            InstanceFieldRef ref = (InstanceFieldRef) right;
//            _writer.writeLoadInstanceField(inMethod, stmt, ref.getField(), (Local) ref.getBase(), left, session);
//        }
//        else if(right instanceof StaticFieldRef)
//        {
//            StaticFieldRef ref = (StaticFieldRef) right;
//            _writer.writeLoadStaticField(inMethod, stmt, ref.getField(), left, session);
//        }
//        else if(right instanceof ArrayRef)
//        {
//            ArrayRef ref = (ArrayRef) right;
//            Local base = (Local) ref.getBase();
//            Value index = ref.getIndex();
//
//            if(index instanceof Local)
//            {
//                    _writer.writeLoadArrayIndex(inMethod, stmt, base, left, (Local) index, session);
//            }
//            else if(index instanceof IntConstant)
//            {
//                    _writer.writeLoadArrayIndex(inMethod, stmt, base, left, null, session);
//            }
//            else
//            {
//                throw new RuntimeException("Cannot handle assignment: " + stmt + " (index: " + index.getClass() + ")");
//            }
//        }
//        else if(right instanceof CastExpr)
//        {
//            CastExpr cast = (CastExpr) right;
//            Value op = cast.getOp();
//
//            if(op instanceof Local)
//            {
//                _writer.writeAssignCast(inMethod, stmt, left, (Local) op, cast.getCastType(), session);
//            }
//            else if(op instanceof NumericConstant)
//            {
//                // seems to always get optimized out, do we need this?
//                _writer.writeAssignCastNumericConstant(inMethod, stmt, left, (NumericConstant) op, cast.getCastType(), session);
//            }
//            else if (op instanceof NullConstant || op instanceof  ClassConstant || op instanceof  StringConstant)
//            {
//                _writer.writeAssignCastNull(inMethod, stmt, left, cast.getCastType(), session);
//            }
//            else
//            {
//                throw new RuntimeException("Cannot handle assignment: " + stmt + " (op: " + op.getClass() + ")");
//            }
//        }
//        else if(right instanceof PhiExpr)
//        {
//            for(Value alternative : ((PhiExpr) right).getValues())
//            {
//                _writer.writeAssignLocal(inMethod, stmt, left, (Local) alternative, session);
//            }
//        }
//        else if (right instanceof BinopExpr)
//        {
//            _writer.writeAssignBinop(inMethod, stmt, left, (BinopExpr) right, session);
//        }
//        else if (right instanceof UnopExpr)
//        {
//            _writer.writeAssignUnop(inMethod, stmt, left, (UnopExpr) right, session);
//        }
//        else if (right instanceof InstanceOfExpr)
//        {
//            InstanceOfExpr expr = (InstanceOfExpr) right;
//            if (expr.getOp() instanceof Local)
//                _writer.writeAssignInstanceOf(inMethod, stmt, left, (Local) expr.getOp(), expr.getCheckType(), session);
//            else // TODO check if this is possible (instanceof on something that is not a local var)
//                _writer.writeUnsupported(inMethod, stmt, session);
//        }
//        else
//        {
//            throw new RuntimeException("Cannot handle assignment: " + stmt + " (right: " + right.getClass() + ")");
//        }
    }

    private void generateLeftNonLocal(IMethod inMethod, AssignStmt stmt, Session session)
    {
//        Value left = stmt.getLeftOp();
//        Value right = stmt.getRightOp();
//
//        // first make sure we have local variable for the right-hand-side.
//        Local rightLocal = null;
//
//        if(right instanceof Local)
//        {
//            rightLocal = (Local) right;
//        }
//        else if(right instanceof StringConstant)
//        {
//            rightLocal = _writer.writeStringConstantExpression(inMethod, stmt, (StringConstant) right, session);
//        }
//        else if(right instanceof NumericConstant)
//        {
//            rightLocal = _writer.writeNumConstantExpression(inMethod, stmt, (NumericConstant) right, session);
//        }
//        else if(right instanceof NullConstant)
//        {
//            rightLocal = _writer.writeNullExpression(inMethod, stmt, left.getType(), session);
//            // NoNullSupport: use the line below to remove Null Constants from the facts.
//            // _writer.writeUnsupported(inMethod, stmt, session);
//        }
//        else if(right instanceof ClassConstant)
//        {
//            rightLocal = _writer.writeClassConstantExpression(inMethod, stmt, (ClassConstant) right, session);
//        }
//        else
//        {
//            throw new RuntimeException("Cannot handle rhs: " + stmt + " (right: " + right.getClass() + ")");
//        }
//
//        // arrays
//        //
//        // NoNullSupport: use the line below to remove Null Constants from the facts.
//        // if(left instanceof ArrayRef && rightLocal != null)
//        if(left instanceof ArrayRef)
//        {
//            ArrayRef ref = (ArrayRef) left;
//            Local base = (Local) ref.getBase();
//            Value index = ref.getIndex();
//
//            if (index instanceof Local)
//                _writer.writeStoreArrayIndex(inMethod, stmt, base, rightLocal, (Local) index, session);
//            else
//                _writer.writeStoreArrayIndex(inMethod, stmt, base, rightLocal, null, session);
//        }
//        // NoNullSupport: use the line below to remove Null Constants from the facts.
//        // else if(left instanceof InstanceFieldRef && rightLocal != null)
//        else if(left instanceof InstanceFieldRef)
//        {
//            InstanceFieldRef ref = (InstanceFieldRef) left;
//            _writer.writeStoreInstanceField(inMethod, stmt, ref.getField(), (Local) ref.getBase(), rightLocal, session);
//        }
//        // NoNullSupport: use the line below to remove Null Constants from the facts.
//        // else if(left instanceof StaticFieldRef && rightLocal != null)
//        else if(left instanceof StaticFieldRef)
//        {
//            StaticFieldRef ref = (StaticFieldRef) left;
//            _writer.writeStoreStaticField(inMethod, stmt, ref.getField(), rightLocal, session);
//        }
//        // NoNullSupport: use the else part below to remove Null Constants from the facts.
//        /*else if(right instanceof NullConstant)
//        {
//            _writer.writeUnsupported(inMethod, stmt, session);
//            // skip, not relevant for pointer analysis
//        }*/
//        else
//        {
//            throw new RuntimeException("Cannot handle assignment: " + stmt + " (right: " + right.getClass() + ")");
//        }
    }

    private void generate(IMethod inMethod, IdentityStmt stmt, Session session)
    {
//        Value left = stmt.getLeftOp();
//        Value right = stmt.getRightOp();
//
//        if(right instanceof CaughtExceptionRef) {
//            // make sure we can jump to statement we do not care about (yet)
//            _writer.writeUnsupported(inMethod, stmt, session);
//
//            /* Handled by ExceptionHandler generation (ExceptionHandler:FormalParam).
//
//               TODO Would be good to check more carefully that a caught
//               exception does not occur anywhere else.
//            */
//            return;
//        }
//        else if(left instanceof Local && right instanceof ThisRef)
//        {
//            _writer.writeAssignLocal(inMethod, stmt, (Local) left, (ThisRef) right, session);
//        }
//        else if(left instanceof Local && right instanceof ParameterRef)
//        {
//            _writer.writeAssignLocal(inMethod, stmt, (Local) left, (ParameterRef) right, session);
//        }
//        else
//        {
//            throw new RuntimeException("Cannot handle identity statement: " + stmt);
//        }
    }

    /**
     * Return statement
     */
    private void generate(IMethod m, IR ir, SSAReturnInstruction instruction)
    {
        SymbolTable symbolTable = ir.getSymbolTable();

        if (instruction.returnsVoid()) {
            _writer.writeReturnVoid(m, instruction);
        }
        else {
            int instructionResult = instruction.getResult();

            if (instructionResult != -1) {
                Local l;
                com.ibm.wala.ssa.Value v = symbolTable.getValue(instructionResult);

                if (v != null) {
                    String s = v.toString();

                    if (v.isStringConstant()) {
                        l = new Local("v" + instructionResult, TypeReference.JavaLangString);
                        _writer.writeStringConstantExpression(m, instruction, l, (ConstantValue) v);
                    } else if (v.isNullConstant()) {
                        l = new Local("v" + instructionResult, TypeReference.Null);
                        _writer.writeNullExpression(m, instruction, l);
                    } else if (symbolTable.isIntegerConstant(instructionResult)) {
                        l = new Local("v" + instructionResult, TypeReference.Int);
                        _writer.writeNumConstantExpression(m, instruction, l, (ConstantValue) v);
                    } else if (symbolTable.isLongConstant(instructionResult)) {
                        l = new Local("v" + instructionResult, TypeReference.Long);
                        _writer.writeNumConstantExpression(m, instruction, l, (ConstantValue) v);
                    } else if (symbolTable.isFloatConstant(instructionResult)) {
                        l = new Local("v" + instructionResult, TypeReference.Float);
                        _writer.writeNumConstantExpression(m, instruction, l, (ConstantValue) v);
                    } else if (symbolTable.isIntegerConstant(instructionResult)) {
                        l = new Local("v" + instructionResult, TypeReference.Int);
                        _writer.writeNumConstantExpression(m, instruction, l, (ConstantValue) v);
                    } else if (symbolTable.isDoubleConstant(instructionResult)) {
                        l = new Local("v" + instructionResult, TypeReference.Double);
                        _writer.writeNumConstantExpression(m, instruction, l, (ConstantValue) v);
                    }
                    else if (symbolTable.isBooleanConstant(instructionResult)) {
                        l = new Local("v" + instructionResult, TypeReference.Boolean);
                        _writer.writeNumConstantExpression(m, instruction, l, (ConstantValue) v);
                    } else if (s.startsWith("#[") || (s.startsWith("#L") && s.endsWith(";"))) {
                        l = new Local("v" + instructionResult, TypeReference.JavaLangClass);
                        _writer.writeClassConstantExpression(m, instruction, l, (ConstantValue) v);
                    } else if (!symbolTable.isConstant(instructionResult)) {
                        String[] localNames = ir.getLocalNames(instruction.iindex, instructionResult);
                        if (localNames != null) {
                            assert localNames.length == 1;
                            l = new Local("v" + instructionResult, localNames[0], m.getReturnType());
                        }
                        //TODO : Check when this occurs
                        else {

                            l = new Local("v" + instructionResult, m.getReturnType());
                        }
                        _writer.writeReturn(m, instruction, l);
                    } else {
                        throw new RuntimeException("Unhandled return statement: " + instruction.toString(symbolTable));
                    }
                    _writer.writeReturn(m, instruction, l);
                }
            }
        }
    }

    private void generate(IMethod inMethod, ThrowStmt stmt, Session session)
    {
//        Value v = stmt.getOp();
//
//        if(v instanceof Local)
//        {
//            _writer.writeThrow(inMethod, stmt, (Local) v, session);
//        }
//        else if(v instanceof NullConstant)
//        {
//            _writer.writeThrowNull(inMethod, stmt, session);
//        }
//        else
//        {
//            throw new RuntimeException("Unhandled throw statement: " + stmt);
//        }
    }
}

