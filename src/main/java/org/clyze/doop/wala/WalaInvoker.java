package org.clyze.doop.wala;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.clyze.doop.common.Database;
import org.clyze.doop.soot.DoopErrorCodeException;
import org.clyze.doop.soot.SootParameters;
import org.clyze.doop.util.filter.GlobClassFilter;
import soot.SootClass;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class WalaInvoker {

    /**
     * Used for logging various messages
     */
    protected Log logger;

    public WalaInvoker() {
        logger =  LogFactory.getLog(getClass());
    }

    private static int shift(String[] args, int index) {
        if(args.length == index + 1) {
            System.err.println("error: option " + args[index] + " requires an argument");
            System.exit(1);
        }
        return index + 1;
    }

    private static boolean isApplicationClass(WalaParameters walaParameters, IClass klass) {
        walaParameters.applicationClassFilter = new GlobClassFilter(walaParameters.appRegex);


        // Change package delimiter from "/" to "."
        return walaParameters.applicationClassFilter.matches(WalaRepresentation.fixTypeString(klass.getName().toString()));
    }

    public void parseParamsAndRun(String[] args) throws IOException {
        WalaParameters walaParameters = new WalaParameters();
        try {
            if (args.length == 0) {
                System.err.println("usage: [options] file...");
                throw new DoopErrorCodeException(0);
            }

            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--android-jars":
                        i = shift(args, i);
                        //walaParameters._allowPhantom = true;
                        walaParameters._android = true;
                        walaParameters._androidJars = args[i];
                        break;
                    case "-i":
                        i = shift(args, i);
                        walaParameters._inputs.add(args[i]);
                        break;
                    case "-l":
                        i = shift(args, i);
                        walaParameters._libraries.add(args[i]);
                        break;
                    case "-p":
                        i = shift(args, i);
                        walaParameters._javaPath = args[i];
                        break;
                    case "-d":
                        i = shift(args, i);
                        walaParameters._outputDir = args[i];
                        break;
                    case "--application-regex":
                        i = shift(args, i);
                        walaParameters.appRegex = args[i];
                        break;
                    case "--fact-gen-cores":
                        i = shift(args, i);
                        try {
                            walaParameters._cores = new Integer(args[i]);
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
        run(walaParameters);
    }

    public void run(WalaParameters walaParameters) throws IOException {
        StringBuilder classPath = new StringBuilder();
        for (int i = 0; i < walaParameters._inputs.size(); i++) {
            if (i == 0)
                classPath.append(walaParameters._inputs.get(i));
            else
                classPath.append(":").append(walaParameters._inputs.get(i));
        }

        for (int i = 0; i < walaParameters._libraries.size(); i++) {
            classPath.append(":").append(walaParameters._libraries.get(i));
        }

        logger.debug("WALA classpath:" + classPath);
        AnalysisScope scope = WalaScopeReader.makeScope(classPath.toString(), null, walaParameters._javaPath);      // Build a class hierarchy representing all classes to analyze.  This step will read the class

        ClassHierarchy cha = null;
        try {
            cha = ClassHierarchyFactory.make(scope);
        } catch (ClassHierarchyException e) {
            e.printStackTrace();
        }

        assert cha != null;
        Iterator<IClass> classes = cha.iterator();
        Database db = new Database(new File(walaParameters._outputDir), false);
        WalaFactWriter walaFactWriter = new WalaFactWriter(db);
        WalaThreadFactory walaThreadFactory = new WalaThreadFactory(walaFactWriter, walaParameters._outputDir, walaParameters._android);

        logger.debug("Number of classes: " + cha.getNumberOfClasses());

        IClass klass;
        Set<IClass> classesSet = new HashSet<>();
        while (classes.hasNext()) {
            klass = classes.next();
            if (isApplicationClass(walaParameters, klass)) {
                walaFactWriter.writeApplicationClass(klass);
            }
            classesSet.add(klass);
        }

        WalaDriver driver = new WalaDriver(walaThreadFactory, cha.getNumberOfClasses(), false, walaParameters._cores, walaParameters._android);
        driver.doInParallel(classesSet);
        driver.shutdown();
        db.flush();
        db.close();
    }
}
