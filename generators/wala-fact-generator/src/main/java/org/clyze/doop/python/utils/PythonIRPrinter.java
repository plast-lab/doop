package org.clyze.doop.python.utils;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.IAnalysisCacheView;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ssa.*;
import com.ibm.wala.types.TypeReference;

import java.io.PrintWriter;
import java.util.Iterator;

public class PythonIRPrinter {
    private final IAnalysisCacheView _cache;

    public PythonIRPrinter(IAnalysisCacheView cache, String outputDir)
    {
        _cache = cache;
    }

    private void printIR(IMethod m, PrintWriter writer)
    {
        IR ir = _cache.getIR(m, Everywhere.EVERYWHERE);
        //printVars(ir,writer);
        SSAInstruction[] instructions = ir.getInstructions();
        SSACFG cfg = ir.getControlFlowGraph();
        SymbolTable symbolTable = ir.getSymbolTable();
        for (int i = 0; i <= cfg.getMaxNumber(); i++) {
            SSACFG.BasicBlock basicBlock = cfg.getNode(i);
            int start = basicBlock.getFirstInstructionIndex();
            int end = basicBlock.getLastInstructionIndex();
            writer.write("\t----BB "+ i +" | " + start +" -> " + end+"\n");

            if(basicBlock instanceof SSACFG.ExceptionHandlerBasicBlock)
            {
                writer.write("\t\tHandler");
                Iterator<TypeReference> types = basicBlock.getCaughtExceptionTypes();
                while(types.hasNext())
                    writer.write(" " + types.next().getName().toString());
                writer.write("\n");
                if(((SSACFG.ExceptionHandlerBasicBlock) basicBlock).getCatchInstruction() != null)
                    writer.write("\t\t\t" + ((SSACFG.ExceptionHandlerBasicBlock) basicBlock).getCatchInstruction().toString(symbolTable) + "\n");
            }
            Iterator<SSAPhiInstruction> phis = basicBlock.iteratePhis();
            while(phis.hasNext())
            {
                SSAPhiInstruction phiInstruction = phis.next();
                writer.write("\t\t\t" + phiInstruction.toString(symbolTable) + "\n");
                //System.out.println(phiInstruction.toString(symbolTable));
            }
            for (int j = start; j <= end; j++) {
                if (instructions[j] != null) {
                    writer.write("\t\t"+j+"\t" + instructions[j].toString(symbolTable) + "\n");

                }
            }
            Iterator<SSAPiInstruction> pis = basicBlock.iteratePis();
            while(pis.hasNext())
            {
                SSAPiInstruction piInstruction = pis.next();
                writer.write("\t\t\t" + piInstruction.toString(symbolTable) + "\n");
                //System.out.println(piInstruction.toString(symbolTable));
            }
        }
    }
}
