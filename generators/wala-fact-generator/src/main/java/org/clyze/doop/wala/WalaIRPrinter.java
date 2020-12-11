package org.clyze.doop.wala;

import com.ibm.wala.analysis.typeInference.TypeInference;
import com.ibm.wala.classLoader.*;
import com.ibm.wala.ipa.callgraph.IAnalysisCacheView;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ssa.*;
import com.ibm.wala.types.TypeReference;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Iterator;

import static org.clyze.doop.wala.WalaUtils.fixTypeString;

class WalaIRPrinter {

    private final IAnalysisCacheView _cache;
    private final String _outputDir;

    WalaIRPrinter(IAnalysisCacheView cache, String outputDir)
    {
        _outputDir = outputDir;
        _cache = cache;
    }

    void printIR(IClass cl)
    {
//        PrintWriter writerOut = new PrintWriter(new EscapedWriter(new OutputStreamWriter((OutputStream)streamOut)));
        BytecodeClass<?> bytecodeClass = (BytecodeClass<?>) cl;
        String fileName = _outputDir + "/IR/" + cl.getReference().getName().toString().replaceAll("/",".").replaceFirst("L","");
        File file = new File(fileName);
        if (file.getParentFile().mkdirs())
            System.out.println("Created directory " + file.getParentFile());

//        Collection<IField> fields = cl.getAllFields();
        Collection<IField> fields = WalaFactWriter.getAllFieldsOfClass(cl);
        Collection<? extends IMethod> methods = cl.getDeclaredMethods();
        Collection<IClass> interfaces = cl.getAllImplementedInterfaces();
        try {
            PrintWriter printWriter = new PrintWriter(file);
            if(bytecodeClass.isPublic())
                printWriter.write("public ");
            else if(bytecodeClass.isPrivate())
                printWriter.write("private ");

            if(bytecodeClass.isAbstract())
                printWriter.write("abstract ");

            if(bytecodeClass.isInterface())
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

                if(m.isNative())
                    printWriter.write("native ");

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

    private void printIR(IMethod m, PrintWriter writer)
    {
        IR ir;
        try {
            ir = _cache.getIR(m, Everywhere.EVERYWHERE);
        }catch(Throwable t){
            writer.write(" Error " + t +" creating IR for method " + m);
            return;
        }
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
                    for(int k = 0; k < instructions[j].getNumberOfUses(); k++){
                        int use = instructions[j].getUse(k);
                        String[] names = ir.getLocalNames(instructions[j].iindex, use);
                        if(names!= null && names.length!=0){
                            writer.write("v"+use +" =[");
                            for(int ii = 0; ii < names.length; ii++){
                                if(names[ii] == null)
                                    continue;
                                writer.write(names[ii]);
//                                if(m.getDeclaringClass().getClassLoader().getName().toString().equals("Application"))
//                                    System.out.println(" name of v" + use + " is "+names[ii]);
                                if(ii!= names.length -1)
                                    writer.write(",");
                            }
                            writer.write("]");
                        }
                    }
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

    //Needs work.
    public void printVars(IR ir, PrintWriter writer)
    {
        SSAInstruction[] instructions = ir.getInstructions();
        SSACFG cfg = ir.getControlFlowGraph();
        TypeInference typeInference = TypeInference.make(ir,true); // Not sure about true for doPrimitives

        for (int i = 0; i <= cfg.getMaxNumber(); i++) {
            SSACFG.BasicBlock basicBlock = cfg.getNode(i);
            int start = basicBlock.getFirstInstructionIndex();
            int end = basicBlock.getLastInstructionIndex();
            for (int j = start; j <= end; j++) {
                if (instructions[j] != null) {

                    for(int k=0;k<instructions[j].getNumberOfUses();k++) {
                        try {
                            writer.write("\t\t" + fixTypeString(typeInference.getType(instructions[j].getUse(k)).getTypeReference().toString()) + " v"+ instructions[j].getUse(k) + "\n");
                        } catch (UnsupportedOperationException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }
                    if(instructions[j].hasDef())
                        writer.write("\t\t" + fixTypeString(typeInference.getType(instructions[j].getDef()).getTypeReference().toString()) +" v"+ instructions[j].getDef() + "\n");


                }
            }
        }
    }
}
