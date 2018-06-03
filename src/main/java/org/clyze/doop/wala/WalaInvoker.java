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
import com.ibm.wala.types.annotations.Annotation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.clyze.doop.common.Database;
import org.clyze.doop.soot.DoopErrorCodeException;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
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
        //walaParameters.applicationClassFilter = new GlobClassFilter(walaParameters.appRegex);
        // Change package delimiter from "/" to "."
        //return walaParameters.applicationClassFilter.matches(WalaUtils.fixTypeString(klass.getName().toString()));
        return(klass.getClassLoader().getName().toString().equals("Application"));
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
                        System.out.println("WALA ANDROID JARS " + walaParameters._androidJars);
                        break;
                    case "-i":
                        i = shift(args, i);
                        walaParameters._inputs.add(args[i]);
                        break;
                    case "-l":
                        i = shift(args, i);
                        walaParameters._appLibraries.add(args[i]);
                        break;
                    case "--uniqueFacts":
                        walaParameters._uniqueFacts = true;
                        break;
                    case "--generate-ir":
                        walaParameters._generateIR = true;
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
                    case "--extra-sensitive-controls":
                        i = shift(args, i);
                        walaParameters._extraSensitiveControls = args[i];
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

//        for (int i = 0; i < walaParameters._appLibraries.size(); i++) {
//            classPath.append(":").append(walaParameters._appLibraries.get(i));
//        }

        System.out.println("WALA classpath:" + classPath);
        for (String lib : walaParameters.getPlatformLibraries())
            System.out.println("Platform Library: " + lib);

        for (String lib : walaParameters.getAppLibraries())
            System.out.println("Application Library: " + lib);

        AnalysisScope scope;
        if(walaParameters._android)
            scope = WalaScopeReader.setUpAndroidAnalysisScope(walaParameters._inputs, "", walaParameters._platformLibraries, walaParameters._appLibraries);
        else
            scope = WalaScopeReader.setupJavaAnalysisScope(walaParameters._inputs,"", walaParameters._platformLibraries, walaParameters._appLibraries);
            //scope = WalaScopeReader.makeScope(classPath.toString(), null, walaParameters._javaPath);      // Build a class hierarchy representing all classes to analyze.  This step will read the class

        ClassHierarchy cha = null;
        try {
            cha = ClassHierarchyFactory.make(scope);
        } catch (ClassHierarchyException e) {
            e.printStackTrace();
        }

        assert cha != null;
        Iterator<IClass> classes = cha.iterator();
        Database db = new Database(new File(walaParameters._outputDir), walaParameters._uniqueFacts);
        WalaFactWriter walaFactWriter = new WalaFactWriter(db, walaParameters._android);
        WalaThreadFactory walaThreadFactory = new WalaThreadFactory(walaFactWriter, walaParameters._outputDir, walaParameters._android);

        //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
        //WARNING: This introduces a dependency to SOOT
        //TODO: Find an alternative way to do this using WALA
        //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
        if(walaParameters._android)
        {
            WalaAndroidXMLParser parser = new WalaAndroidXMLParser(walaParameters._inputs, walaFactWriter, walaParameters._extraSensitiveControls);
            parser.writeComponents();
        }
        System.out.println("Number of classes: " + cha.getNumberOfClasses());

        IAnalysisCacheView cache;
        if(walaParameters._android)
            cache = new AnalysisCacheImpl(new DexIRFactory());
        else
            cache = new AnalysisCacheImpl();

        IClass klass;
        Set<IClass> classesSet = new HashSet<>();
        Set<String> signaturePolymorphicMethods = new HashSet<>();
        while (classes.hasNext()) {
            klass = classes.next();
            if (isApplicationClass(walaParameters, klass)) {
                walaFactWriter.writeApplicationClass(klass);
            }
            classesSet.add(klass);
            for(IMethod m: klass.getDeclaredMethods()) {
                addIfSignaturePolymorphic(m, signaturePolymorphicMethods);
                cache.getIR(m);
            }
        }
        walaFactWriter.setSignaturePolyMorphicMethods(signaturePolymorphicMethods);

        WalaDriver driver = new WalaDriver(walaThreadFactory, cha.getNumberOfClasses(), false, walaParameters._cores, walaParameters._android, cache);
        driver.doInParallel(classesSet);
        driver.shutdown();

        if(walaFactWriter.getNumberOfPhantomTypes() > 0)
            System.out.println("WARNING: Input contains phantom types. \nNumber of phantom types:" + walaFactWriter.getNumberOfPhantomTypes());
        if(walaFactWriter.getNumberOfPhantomMethods() > 0)
            System.out.println("WARNING: Input contains phantom methods. \nNumber of phantom methods:" + walaFactWriter.getNumberOfPhantomMethods());
        if(walaFactWriter.getNumberOfPhantomBasedMethods() > 0)
            System.out.println("WARNING: Input contains phantom based methods. \nNumber of phantom based methods:" + walaFactWriter.getNumberOfPhantomBasedMethods());
        db.flush();
        db.close();
    }

    private void addIfSignaturePolymorphic(IMethod m, Set <String> signaturePolymorphics)
    {
        Collection<Annotation> annotations = m.getAnnotations();
        String className = WalaUtils.fixTypeString(m.getDeclaringClass().getName().toString());
        for(Annotation ann: annotations)
        {
            if(ann.getType().toString().contains("PolymorphicSignature"))
            {
                //System.out.println(m.toString());
                signaturePolymorphics.add(className + ":" + m.getName().toString());
            }
        }
    }

}
