package org.clyze.doop.wala;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.dalvik.classLoader.DexIRFactory;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.IAnalysisCacheView;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.util.ref.ReferenceCleanser;
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

public class Main {

    private final static int WIPE_SOFT_CACHE_INTERVAL = 2500;
    private static int wipeCount = 0;

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
        return walaParameters.applicationClassFilter.matches(WalaUtils.fixTypeString(klass.getName().toString()));
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
                        walaParameters._appLibraries.add(args[i]);
                        break;
                    case "-el":
                        i = shift(args, i);
                        walaParameters._platformLibraries.add(args[i]);
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

    public static void run(WalaParameters walaParameters) throws IOException {
        String classPath = "";
        for (int i = 0; i < walaParameters._inputs.size(); i++) {
            if (i == 0)
                classPath += walaParameters._inputs.get(i);
            else
                classPath += ":" + walaParameters._inputs.get(i);
        }

        for (int i = 0; i < walaParameters._appLibraries.size(); i++) {
            classPath += ":" + walaParameters._appLibraries.get(i);
        }

        System.out.println("WALA classpath:" + classPath);

        // Build a class hierarchy representing all classes to analyze.  This step will read the class
        // files and organize them into a tree.
        AnalysisScope scope = WalaScopeReader.makeScope(classPath, null, walaParameters._javaPath);

        ClassHierarchy cha = null;
        try {
            cha = ClassHierarchyFactory.make(scope);
        } catch (ClassHierarchyException e) {
            e.printStackTrace();
        }

        Iterator<IClass> classes = cha.iterator();
        Database db = new Database(new File(walaParameters._outputDir), false);
        WalaFactWriter walaFactWriter = new WalaFactWriter(db, walaParameters._android);
        WalaThreadFactory walaThreadFactory = new WalaThreadFactory(walaFactWriter, walaParameters._outputDir, walaParameters._android);

        System.out.println("Number of classes: " + cha.getNumberOfClasses());

        IAnalysisCacheView cache;
        if(walaParameters._android)
            cache = new AnalysisCacheImpl(new DexIRFactory());
        else
            cache = new AnalysisCacheImpl();

        IClass klass;
        int totalClasses = 0;
        Set<IClass> classesSet = new HashSet<>();
        while (classes.hasNext()) {
            klass = classes.next();
            if (isApplicationClass(walaParameters, klass)) {
                walaFactWriter.writeApplicationClass(klass);
            }
            totalClasses++;
            classesSet.add(klass);
            for(IMethod m: klass.getDeclaredMethods()) {
                cache.getIR(m);
                wipeSoftCaches();
            }
        }

        WalaDriver driver = new WalaDriver(walaThreadFactory, totalClasses, false, walaParameters._cores, walaParameters._android, cache);

        driver.doInParallel(classesSet);
        driver.shutdown();
        db.flush();
        db.close();

    }

    private static void wipeSoftCaches() {
        wipeCount++;
        if (wipeCount >= WIPE_SOFT_CACHE_INTERVAL) {
            wipeCount = 0;
            ReferenceCleanser.clearSoftCaches();
        }
    }
}
