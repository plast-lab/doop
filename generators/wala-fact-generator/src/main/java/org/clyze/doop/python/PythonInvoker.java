package org.clyze.doop.python;

import com.ibm.wala.cast.python.ir.PythonCAstToIRTranslator;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.IAnalysisCacheView;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.IR;
import java.io.File;
import java.io.IOException;
import java.util.*;
import org.clyze.doop.common.DoopErrorCodeException;
import org.clyze.doop.python.utils.PythonDatabase;
import org.clyze.doop.python.utils.PythonPredicateFile;

public class PythonInvoker {

    public void main(String[] args) throws IOException {
        PythonParameters parameters = new PythonParameters();
        System.out.println("Python Fact Gen!!");
        try {
            if (args.length == 0) {
                System.err.println("Usage: wala-fact-generator [options] file...");
                throw DoopErrorCodeException.error0();
            }
            parameters.initFromArgs(args);
        } catch(DoopErrorCodeException errCode) {
            int n = errCode.getErrorCode();
            if (n != 0)
                System.err.println("Exiting with code " + n);
        } catch(Exception exc) {
            exc.printStackTrace();
        }
        run(parameters);
    }

    private void run(PythonParameters parameters) throws IOException
    {
        PythonDatabase db = new PythonDatabase(new File(parameters.getOutputDir()));
        PythonFactWriter factWriter = new PythonFactWriter(db);
        int numOfFailures = 0;
        int numOfEmptyCha = 0;
        PythonCAstToIRTranslator.setSingleFileAnalysis(parameters._singleFileAnalysis);
        for(String inputFile: parameters.getInputs()) {
            try{
                int numOfClassesInCha = 0;
                //PythonIREngine pythonIREngine = new PythonIREngine(parameters._inputs);
                List<String> singleInputList= new ArrayList<>(1);
                singleInputList.add(inputFile);
                PythonIREngine pythonIREngine = new PythonIREngine(singleInputList);
                pythonIREngine.buildAnalysisScope();
                IClassHierarchy cha = pythonIREngine.buildClassHierarchy();

                IAnalysisCacheView cache = pythonIREngine.getAnalysisCache();
                Iterator<IClass> classes = cha.iterator();
                Set<IClass> classSet = new HashSet<>();
                while (classes.hasNext()) {
                    numOfClassesInCha++;
                    IClass klass = classes.next();
                    classSet.add(klass);
                    String sourceFileName = "";
                    try {
                        sourceFileName = klass.getSourceFileName();
                    } catch (NullPointerException ex) {
                        System.err.println("Error reading " + klass + " source file name:" + ex.getMessage());
                    }
                    //System.out.println("class: " + klass.toString() + " in file: " + sourceFileName);
                    Collection<? extends IMethod> methods = klass.getDeclaredMethods();
                    for (IMethod m : methods) {
                        try {
                            m.getName();
                            //System.out.println("\t" + m.getSignature());
                            IR ir = cache.getIR(m);
                            System.out.println(ir.toString());
                        } catch (Exception ex) {
                            System.err.println("ERROR: cannot get IR for method: " + m);
                            ex.printStackTrace();
                        }
                    }
                }
                Runnable pythonFactGenerator = new PythonFactGenerator(factWriter, classSet, parameters.getOutputDir(), cache);
                pythonFactGenerator.run();
                if(numOfClassesInCha == 6) {
                    numOfEmptyCha++;
                    factWriter.writeError(PythonPredicateFile.EMPTY_CHA, inputFile);
                }
            }catch (Throwable t){
                t.printStackTrace();
                numOfFailures++;

                factWriter.writeError(PythonPredicateFile.ERROR_OR_EXCEPTON, inputFile, t.toString());
            }
        }
        factWriter.writeRootFolder();
        System.out.println("Failed for " + numOfFailures + " out of " + parameters.getInputs().size() + " python script files.");
        System.out.println("Empty Class Hierarchy for " + numOfEmptyCha + " out of " + parameters.getInputs().size() + " python script files.");

        db.close();
    }
}
