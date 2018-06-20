package org.clyze.doop.python;

import com.ibm.wala.analysis.typeInference.TypeInference;
import com.ibm.wala.cast.analysis.typeInference.AstTypeInference;
import com.ibm.wala.cast.python.ssa.PythonInvokeInstruction;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.IAnalysisCacheView;
import com.ibm.wala.ssa.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.clyze.doop.python.utils.PythonIRPrinter;
import org.clyze.doop.wala.Session;

import java.util.Iterator;
import java.util.Set;

public class PythonFactGenerator implements Runnable{
    protected Log logger;

    private PythonFactWriter _writer;
    private IAnalysisCacheView cache;
    private PythonIRPrinter IRPrinter;
    private Set<IClass> _iClasses;

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
            //IRPrinter.printIR(iClass);
//            _writer.writeClassOrInterfaceType(iClass);
//            //TODO: Handling of Arrays?
//            if(iClass.isAbstract())
//                _writer.writeClassModifier(iClass, "abstract");
////            if(Modifier.isFinal(modifiers))
////                _writer.writeClassModifier(iClass, "final");
//            if(iClass.isPublic())
//                _writer.writeClassModifier(iClass, "public");
//            if(iClass.isPrivate())
//                _writer.writeClassModifier(iClass, "private");
//
//            // the isInterface condition prevents Object as superclass of interface
//            if (iClass.getSuperclass() != null && !iClass.isInterface()) {
//                _writer.writeDirectSuperclass(iClass, iClass.getSuperclass());
//            }
//
//            for (IClass i : iClass.getAllImplementedInterfaces()) {
//                _writer.writeDirectSuperinterface(iClass, i);
//            }
//
//            iClass.getDeclaredInstanceFields().forEach(this::generate);
//            try{
//                iClass.getDeclaredStaticFields().forEach(this::generate);
//            }catch (NullPointerException exc) //For some reason in DexClasses .getDeclaredStaticFields() can throw a NullPointerException
//            {
//                ;
//            }


            for (IMethod m : iClass.getDeclaredMethods()) {
                Session session = new org.clyze.doop.wala.Session();
                try {
                    generate(m, session);
                }
                catch (Exception exc) {
                    System.err.println("Error while processing method: " + m + " of class " +m.getDeclaringClass());
                    exc.printStackTrace();
                    throw exc;
                }
            }
        }
    }

    private void generate(IField f) {

    }

    private void generate(IMethod m, Session session) {
        IR ir = cache.getIR(m);
        generate(m, ir, session);
    }

    private void generate(IMethod m, IR ir, Session session) {
        SSAInstruction[] instructions = ir.getInstructions();
        SSACFG cfg = ir.getControlFlowGraph();
        SSACFG.ExceptionHandlerBasicBlock previousHandlerBlock = null;
        TypeInference typeInference;

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
                    //this.generateDefs(m, ir, instructions[j], typeInference);
                    //this.generateUses(m, ir, instructions[j], session, typeInference);
                    if (instructions[j] instanceof SSAReturnInstruction) {
                        //generate(m, ir, (SSAReturnInstruction) instructions[j], session, typeInference);
                    } else if (instructions[j] instanceof SSABinaryOpInstruction) {
                        //generate(m, ir, (SSABinaryOpInstruction) instructions[j], session, typeInference);
                    } else if (instructions[j] instanceof SSAMonitorInstruction) {
                        //generate(m, ir, (SSAMonitorInstruction) instructions[j], session, typeInference);
                    } else if (instructions[j] instanceof SSAThrowInstruction) {
                        //generate(m, ir, (SSAThrowInstruction) instructions[j], session, typeInference);
                    } else if (instructions[j] instanceof PythonInvokeInstruction) {
                        //generate(m, ir, (SSAInvokeInstruction) instructions[j], session, typeInference);
                    } else if (instructions[j] instanceof SSAAbstractInvokeInstruction) {
                        //generate(m, ir, (SSAInvokeInstruction) instructions[j], session, typeInference);
                    } else if (instructions[j] instanceof SSAGetInstruction) {System.out.println("get" + instructions[j].toString());
                        //generate(m, ir, (SSAGetInstruction) instructions[j], session, typeInference);
                    } else if (instructions[j] instanceof SSAPutInstruction) {System.out.println("oi" + instructions[j].toString());
                        //generate(m, ir, (SSAPutInstruction) instructions[j], session, typeInference);
                    } else if (instructions[j] instanceof SSAUnaryOpInstruction) {
                        //generate(m, ir, (SSAUnaryOpInstruction) instructions[j], session, typeInference);
                    } else if (instructions[j] instanceof SSAArrayLengthInstruction) {
                        //generate(m, ir, (SSAArrayLengthInstruction) instructions[j], session, typeInference);
                    } else if (instructions[j] instanceof SSAArrayLoadInstruction) {
                        //generate(m, ir, (SSAArrayLoadInstruction) instructions[j], session, typeInference);
                    } else if (instructions[j] instanceof SSAArrayStoreInstruction) {
                        //generate(m, ir, (SSAArrayStoreInstruction) instructions[j], session, typeInference);
                    } else if (instructions[j] instanceof SSANewInstruction) {
                        //generate(m, ir, (SSANewInstruction) instructions[j], session, typeInference);
                    } else if (instructions[j] instanceof SSAComparisonInstruction) {
                        //generate(m, ir, (SSAComparisonInstruction) instructions[j], session, typeInference);
                    } else if (instructions[j] instanceof SSALoadMetadataInstruction) {
                        //generate(m, ir, (SSALoadMetadataInstruction) instructions[j], session, typeInference);
                    } else if (instructions[j] instanceof SSAInstanceofInstruction) {
                        //generate(m, ir, (SSAInstanceofInstruction) instructions[j], session, typeInference);
                    } else if (instructions[j] instanceof SSACheckCastInstruction) {
                        //generate(m, ir, (SSACheckCastInstruction) instructions[j], session, typeInference);
                    } else if (instructions[j] instanceof SSAConversionInstruction) {
                        //generate(m, ir, (SSAConversionInstruction) instructions[j], session, typeInference);
                    } else if (instructions[j] instanceof SSASwitchInstruction) {
                        session.calcInstructionNumber(instructions[j]);
                    } else if (instructions[j] instanceof SSAGotoInstruction) {
                        session.calcInstructionNumber(instructions[j]);
                    } else if (instructions[j] instanceof SSAConditionalBranchInstruction) {
                        session.calcInstructionNumber(instructions[j]);
                    } else {
                        System.out.println("Unknown instruction: " + instructions[j].getClass().getSimpleName());
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

}
