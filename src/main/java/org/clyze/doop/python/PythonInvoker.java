package org.clyze.doop.python;

import com.ibm.wala.cast.ir.ssa.AstIRFactory;
import com.ibm.wala.cast.python.loader.PythonLoaderFactory;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.SourceURLModule;
import com.ibm.wala.ipa.callgraph.*;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ipa.cha.SeqClassHierarchyFactory;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.IRFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.clyze.doop.soot.DoopErrorCodeException;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.Iterator;

public class PythonInvoker {

    protected Log logger;

    public PythonInvoker() {
        logger =  LogFactory.getLog(getClass());
    }

    private static int shift(String[] args, int index) {
        if(args.length == index + 1) {
            System.err.println("error: option " + args[index] + " requires an argument");
            System.exit(1);
        }
        return index + 1;
    }

    public void main(String[] args) throws IOException {
        PythonParameters parameters = new PythonParameters();
        System.out.println("Python Fact Gen!!");
        try {
            if (args.length == 0) {
                System.err.println("usage: [options] file...");
                throw new DoopErrorCodeException(0);
            }

            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "-i":
                        i = shift(args, i);
                        parameters._inputs.add(args[i]);
                        break;
                    case "-l":
                        i = shift(args, i);
                        parameters._appLibraries.add(args[i]);
                        break;
                    case "--uniqueFacts":
                        parameters._uniqueFacts = true;
                        break;
                    case "--generate-ir":
                        parameters._generateIR = true;
                        break;
//                    case "-el":
//                        i = shift(args, i);
//                        parameters._platformLibraries.add(args[i]);
//                        break;
                    case "-d":
                        i = shift(args, i);
                        parameters._outputDir = args[i];
                        break;
                    case "--fact-gen-cores":
                        i = shift(args, i);
                        try {
                            parameters._cores = new Integer(args[i]);
                        } catch (NumberFormatException nfe) {
                            System.out.println("Invalid cores argument: " + args[i]);
                        }
                        break;
                    default:
                        if (args[i].charAt(0) == '-') {
                            System.err.println("error: unrecognized option: " + args[i]);
                            throw new DoopErrorCodeException(6);
                        }
                        break;
                }
            }
        } catch(DoopErrorCodeException errCode) {
            int n = errCode.getErrorCode();
            if (n != 0)
                System.err.println("Exiting with code " + n);
        }
        catch(Exception exc) {
            exc.printStackTrace();
        }
        run(parameters);
    }

    public void run(PythonParameters parameters) throws IOException
    {
        PythonIREngine pythonIREngine = new PythonIREngine(parameters._inputs);
        AnalysisScope scope = pythonIREngine.buildAnalysisScope();
        IClassHierarchy cha = pythonIREngine.buildClassHierarchy();

        IAnalysisCacheView cache = pythonIREngine.getAnalysisCache();
        Iterator<IClass> classes = cha.iterator();
        while(classes.hasNext())
        {
            IClass klass = classes.next();
            String sourceFileName="";
            try{
                sourceFileName = klass.getSourceFileName();
            }catch(NullPointerException ex)
            {

            }
            System.out.println("class: " + klass.toString() + " in file:" + sourceFileName);
            Collection<? extends IMethod> methods = klass.getDeclaredMethods();
            for(IMethod m : methods)
            {
                m.getName();
                System.out.println("\t"+m.getSignature());
                IR ir = cache.getIR(m);
                System.out.println(ir.toString());
            }
        }
    }
}
