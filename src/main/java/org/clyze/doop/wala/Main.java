package org.clyze.doop.wala;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.IAnalysisCacheView;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAOptions;
import com.ibm.wala.util.config.AnalysisScopeReader;
import org.clyze.doop.common.Database;
import org.clyze.doop.soot.DoopErrorCodeException;
import org.clyze.doop.soot.Driver;
import org.clyze.doop.soot.ThreadFactory;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;

public class Main {

    private static int shift(String[] args, int index) {
        if(args.length == index + 1) {
            System.err.println("error: option " + args[index] + " requires an argument");
            System.exit(1);
        }
        return index + 1;
    }

    public static void main(String[] args) throws IOException {
        WalaParameters walaParameters = new WalaParameters();
        try {
            if (args.length == 0) {
                System.err.println("usage: [options] file...");
                throw new DoopErrorCodeException(0);
            }

            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "-i":
                        i = shift(args, i);
                        walaParameters._inputs.add(args[i]);
                        break;
                    case "-l":
                        i = shift(args, i);
                        walaParameters._libraries.add(args[i]);
                        break;
                    case "-d":
                        i = shift(args, i);
                        walaParameters._outputDir = args[i];
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
        run(walaParameters);
    }

    public static void run(WalaParameters walaParameters) throws IOException {
        for (String input : walaParameters._inputs) {
            AnalysisScope scope = AnalysisScopeReader.makeJavaBinaryAnalysisScope(input, null);      // Build a class hierarchy representing all classes to analyze.  This step will read the class
            // files and organize them into a tree.
            ClassHierarchy cha = null;
            try {
                cha = ClassHierarchyFactory.make(scope);
            } catch (ClassHierarchyException e) {
                e.printStackTrace();
            }
            // Set up options which govern analysis choices.  In particular, we will use all Pi nodes when
            // building the IR.

            // Create an object which caches IRs and related information, reconstructing them lazily on demand.
            Iterator<IClass> classes = cha.iterator();      //IMethod m ;

            Database db = new Database(new File(walaParameters._outputDir), false);
            WalaFactWriter walaFactWriter = new WalaFactWriter(db);
            WalaThreadFactory factory = new WalaThreadFactory(walaFactWriter);
            WalaDriver driver = new WalaDriver(factory, cha.getNumberOfClasses(), 1);

            System.out.println("Number of classes: " + cha.getNumberOfClasses());
            driver.doInParallel(classes);
        }

    }
}
