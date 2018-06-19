package org.clyze.doop.python;

import com.ibm.wala.cast.python.loader.PythonLoaderFactory;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ipa.cha.SeqClassHierarchyFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.clyze.doop.soot.DoopErrorCodeException;

import java.io.IOException;

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
        AnalysisScope scope = PythonScopeBuilder.buildAnalysisScope(parameters._inputs);
        IClassHierarchy cha = null;
        PythonLoaderFactory loader = new PythonLoaderFactory();
        try {
            cha = SeqClassHierarchyFactory.make(scope, loader);
        } catch (ClassHierarchyException e) {
            System.err.println("Exception when creating a Class Hierarchy: "+ e);
        }
    }

}
