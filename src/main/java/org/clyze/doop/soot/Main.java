package org.clyze.doop.soot;

import org.clyze.doop.common.Database;
import org.clyze.doop.util.filter.GlobClassFilter;
import org.objectweb.asm.ClassReader;
import soot.*;
import soot.SourceLocator.FoundFile;
import soot.jimple.infoflow.android.SetupApplication;
import soot.jimple.infoflow.android.axml.AXmlNode;
import soot.jimple.infoflow.android.manifest.ProcessManifest;
import soot.jimple.infoflow.android.resources.ARSCFileParser;
import soot.jimple.infoflow.android.resources.DirectLayoutFileParser;
import soot.jimple.infoflow.android.resources.PossibleLayoutControl;
import soot.options.Options;

import java.io.File;
import java.io.FileInputStream;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;

import static soot.DexClassProvider.classesOfDex;
import static soot.jimple.infoflow.android.InfoflowAndroidConfiguration.CallbackAnalyzer.Fast;

public class Main {

    private static int shift(String[] args, int index) {
        if(args.length == index + 1) {
            System.err.println("error: option " + args[index] + " requires an argument");
            System.exit(1);
        }
        return index + 1;
    }

    private static boolean isApplicationClass(SootParameters sootParameters, SootClass klass) {
        sootParameters.applicationClassFilter = new GlobClassFilter(sootParameters.appRegex);

        return sootParameters.applicationClassFilter.matches(klass.getName());
    }

    public static void main(String[] args) {
        SootParameters sootParameters = new SootParameters();
        try {
            if (args.length == 0) {
                System.err.println("usage: [options] file...");
                throw new DoopErrorCodeException(0);
            }

            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--full":
                        if (sootParameters._mode != null) {
                            System.err.println("error: duplicate mode argument");
                            throw new DoopErrorCodeException(1);
                        }
                        sootParameters._mode = SootParameters.Mode.FULL;
                        break;
                    case "-d":
                        i = shift(args, i);
                        sootParameters._outputDir = args[i];
                        break;
                    case "--main":
                        i = shift(args, i);
                        sootParameters._main = args[i];
                        break;
                    case "--ssa":
                        sootParameters._ssa = true;
                        break;
                    case "--android-jars":
                        i = shift(args, i);
                        sootParameters._allowPhantom = true;
                        sootParameters._android = true;
                        sootParameters._androidJars = args[i];
                        break;
                    case "-l":
                        i = shift(args, i);
                        sootParameters._libraries.add(args[i]);
                        break;
                    case "-lsystem":
                        String javaHome = System.getProperty("java.home");
                        sootParameters._libraries.add(javaHome + File.separator + "lib" + File.separator + "rt.jar");
                        sootParameters._libraries.add(javaHome + File.separator + "lib" + File.separator + "jce.jar");
                        sootParameters._libraries.add(javaHome + File.separator + "lib" + File.separator + "jsse.jar");
                        break;
                    case "--deps":
                        i = shift(args, i);
                        String folderName = args[i];
                        File f = new File(folderName);
                        if (!f.exists()) {
                            System.err.println("Dependency folder " + folderName + " does not exist");
                            throw new DoopErrorCodeException(0);
                        } else if (!f.isDirectory()) {
                            System.err.println("Dependency folder " + folderName + " is not a directory");
                            throw new DoopErrorCodeException(0);
                        }
                        for (File file : f.listFiles()) {
                            if (file.isFile() && file.getName().endsWith(".jar")) {
                                sootParameters._libraries.add(file.getCanonicalPath());
                            }
                        }
                        break;
                    case "--application-regex":
                        i = shift(args, i);
                        sootParameters.appRegex = args[i];
                        break;
                    case "--allow-phantom":
                        sootParameters._allowPhantom = true;
                        break;
                    case "--run-flowdroid":
                        sootParameters._runFlowdroid = true;
                        break;
                    case "--only-application-classes-fact-gen":
                        sootParameters._onlyApplicationClassesFactGen = true;
                        break;
                    case "--generate-jimple":
                        sootParameters._generateJimple = true;
                        break;
                    case "--stdout":
                        sootParameters._toStdout = true;
                        break;
                    case "--noFacts":
                        sootParameters._noFacts = true;
                        break;
                    case "--uniqueFacts":
                        sootParameters._uniqueFacts = true;
                        break;
                    case "-h":
                    case "--help":
                    case "-help":
                        System.err.println("\nusage: [options] file");
                        System.err.println("options:");
                        System.err.println("  --main <class>                        Specify the main name of the main class");
                        System.err.println("  --ssa                                 Generate SSA facts, enabling flow-sensitive analysis");
                        System.err.println("  --full                                Generate facts by full transitive resolution");
                        System.err.println("  -d <directory>                        Specify where to generate csv fact files");
                        System.err.println("  -l <archive>                          Find classes in jar/zip archive");
                        System.err.println("  -lsystem                              Find classes in default system classes");
                        System.err.println("  --deps <directory>                    Add jars in this directory to the class lookup path");
                        System.err.println("  --only-application-classes-fact-gen   Generate facts only for application classes");
                        System.err.println("  --noFacts                             Don't generate facts (just empty files -- used for debugging)");
                        System.err.println("  --uniqueFacts                         Eliminate redundancy from facts");

                        System.err.println("  --generate-jimple                     Generate Jimple/Shimple files instead of facts");
                        System.err.println("  --generate-jimple-help                Show help information regarding bytecode2jimple");
                        throw new DoopErrorCodeException(0);
                    case "--generate-jimple-help":
                        System.err.println("\nusage: [options] file");
                        System.err.println("options:");
                        System.err.println("  --ssa                                 Generate Shimple files (use SSA for variables)");
                        System.err.println("  --full                                Generate Jimple/Shimple files by full transitive resolution");
                        System.err.println("  --stdout                              Write Jimple/Shimple to stdout");
                        System.err.println("  -d <directory>                        Specify where to generate files");
                        System.err.println("  -l <archive>                          Find classes in jar/zip archive");
                        System.err.println("  -lsystem                              Find classes in default system classes");
                        System.err.println("  --android-jars <archive>              The main android library jar (for android apks). The same jar should be provided in the -l option");
                        throw new DoopErrorCodeException(0);
                    default:
                        if (args[i].charAt(0) == '-') {
                            System.err.println("error: unrecognized option: " + args[i]);
                            throw new DoopErrorCodeException(0);
                        } else {
                            sootParameters._inputs.add(args[i]);
                        }
                        break;
                }
            }

            if(sootParameters._mode == null) {
                sootParameters._mode = SootParameters.Mode.INPUTS;
            }

            if (sootParameters._toStdout && !sootParameters._generateJimple) {
                System.err.println("error: --stdout must be used with --generate-jimple");
                throw new DoopErrorCodeException(1);
            }
            if (sootParameters._toStdout && sootParameters._outputDir != null) {
                System.err.println("error: --stdout and -d options are not compatible");
                throw new DoopErrorCodeException(2);
            }
            else if ((sootParameters._inputs.stream().filter(s -> s.endsWith(".apk")).count() > 0) &&
                    (!sootParameters._android)) {
                System.err.println("error: the --platform parameter is mandatory for .apk inputs");
                throw new DoopErrorCodeException(3);
            }
            else if (!sootParameters._toStdout && sootParameters._outputDir == null) {
                sootParameters._outputDir = System.getProperty("user.dir");
            }
            produceFacts(sootParameters);
        }
        catch(DoopErrorCodeException errCode) {
            int n = errCode.getErrorCode();
            if (n != 0)
                System.err.println("Exiting with code " + n);
        }
        catch(Exception exc) {
            exc.printStackTrace();
        }
    }

    private static void produceFacts(SootParameters sootParameters) throws Exception {
        SootMethod dummyMain = null;

        Options.v().set_output_dir(sootParameters._outputDir);

        if (sootParameters._ssa) {
            Options.v().set_via_shimple(true);
            Options.v().set_output_format(Options.output_format_shimple);
        } else {
            Options.v().set_output_format(Options.output_format_jimple);
        }
        //soot.options.Options.v().set_drop_bodies_after_load(true);
        Options.v().set_keep_line_number(true);

        PropertyProvider propertyProvider = new PropertyProvider();
        Set<SootClass> classes = new HashSet<>();
        Set<String> classesInApplicationJar = new HashSet<>();
        List<AXmlNode> appServices = null;
        List<AXmlNode> appActivities = null;
        List<AXmlNode> appContentProviders = null;
        List<AXmlNode> appBroadcastReceivers = null;
        Map<String, Set<String>> appCallbackMethods = null;
        Map<String, Set<PossibleLayoutControl>> appUserControls = null;
        File apk = null;

        if (sootParameters._android) {
            String apkLocation = sootParameters._inputs.get(0);
            apk = new File(apkLocation);
            SetupApplication app = new SetupApplication(sootParameters._androidJars, apkLocation);
            Options.v().set_process_multiple_dex(true);
            Options.v().set_src_prec(Options.src_prec_apk);

            if (sootParameters._runFlowdroid) {
                app.getConfig().setCallbackAnalyzer(Fast);
                String filename = Main.class.getClassLoader().getResource("SourcesAndSinks.txt").getFile();
                app.calculateSourcesSinksEntrypoints(filename);
                dummyMain = app.getDummyMainMethod();
                if (dummyMain == null) {
                    throw new RuntimeException("Dummy main null");
                }
            } else {
                ProcessManifest processMan = new ProcessManifest(apkLocation);
                String appPackageName = processMan.getPackageName();
                ARSCFileParser resParser = new ARSCFileParser();
                resParser.parse(apkLocation);
                DirectLayoutFileParser lfp = new DirectLayoutFileParser(appPackageName, resParser);
                lfp.registerLayoutFilesDirect(apkLocation);
                lfp.parseLayoutFileDirect(apkLocation);

                // now collect the facts we need
                appServices = processMan.getServices();
                appActivities = processMan.getActivities();
                appContentProviders = processMan.getProviders();
                appBroadcastReceivers = processMan.getReceivers();
                appCallbackMethods = lfp.getCallbackMethods();
                appUserControls = lfp.getUserControls();

//            System.out.println("All entry points:\n" + appEntrypoints);
//            System.out.println("\nServices:\n" + appServices + "\nActivities:\n" + appActivities + "\nProviders:\n" + appContentProviders + "\nCallback receivers:\n" +appBroadcastReceivers);
//            System.out.println("\nCallback methods:\n" + appCallbackMethods + "\nUser controls:\n" + appUserControls);
            }

        } else {
            Options.v().set_src_prec(Options.src_prec_class);

            JarEntry entry;

            try (JarInputStream jin = new JarInputStream(new FileInputStream(sootParameters._inputs.get(0))); JarFile jarFile = new JarFile(sootParameters._inputs.get(0))) {
                /* List all JAR entries */
                while ((entry = jin.getNextJarEntry()) != null) {
                    /* Skip directories */
                    if (entry.isDirectory())
                        continue;

                    /* Skip non-class files and non-property files */
                    if (!entry.getName().endsWith(".class") && !entry.getName().endsWith(".properties"))
                        continue;

                    if (entry.getName().endsWith(".class")) {
                        ClassReader reader = new ClassReader(jarFile.getInputStream(entry));
                        classesInApplicationJar.add(reader.getClassName().replace("/", "."));
                    }

                    if (entry.getName().endsWith(".properties")) {
                        propertyProvider.addProperties((new FoundFile(sootParameters._inputs.get(0), entry.getName())));
                    }
                }
            }
        }

        Scene scene = Scene.v();
        scene.setSootClassPath("");
        for (String input : sootParameters._inputs) {
            if (input.endsWith(".jar") || input.endsWith(".jar")) {
                System.out.println("Adding archive: " + input);
            }
            else {
                System.out.println("Adding file: " + input);
            }
            scene.extendSootClassPath(input);
        }

        for (String lib : sootParameters._libraries) {
            System.out.println("Adding archive for resolving: " + lib);
            scene.extendSootClassPath(lib);
        }

        if (sootParameters._main != null) {
            Options.v().set_main_class(sootParameters._main);
        }

        if (sootParameters._mode == SootParameters.Mode.FULL) {
            Options.v().set_full_resolver(true);
        }

        if (sootParameters._allowPhantom) {
            Options.v().set_allow_phantom_refs(true);
        }

        if (sootParameters._android) {
            for (String className : classesOfDex(apk)) {
                SootClass c = scene.loadClass(className, SootClass.BODIES);
                classes.add(c);
            }

            System.out.println("Classes found in apk: " + classesOfDex(apk).size());
        } else {
            for (String className : classesInApplicationJar) {
                SootClass c = scene.loadClass(className, SootClass.BODIES);
                classes.add(c);
            }

            System.out.println("Classes in application jar: " + classesInApplicationJar.size());

            /*
             * Set resolution level for sun.net.www.protocol.ftp.FtpURLConnection
             * to 1 (HIERARCHY) before calling produceFacts(). The following line is necessary to avoid
             * a runtime exception when running soot with java 1.8, however it leads to different
             * input fact generation thus leading to different analysis results
             */
            scene.addBasicClass("sun.net.www.protocol.ftp.FtpURLConnection", 1);
            /*
             * For simulating the FileSystem class, we need the implementation
             * of the FileSystem, but the classes are not loaded automatically
             * due to the indirection via native code.
             */
            addCommonDynamicClass(scene, "java.io.UnixFileSystem");
            addCommonDynamicClass(scene, "java.io.WinNTFileSystem");
            addCommonDynamicClass(scene, "java.io.Win32FileSystem");

            /* java.net.URL loads handlers dynamically */
            addCommonDynamicClass(scene, "sun.net.www.protocol.file.Handler");
            addCommonDynamicClass(scene, "sun.net.www.protocol.ftp.Handler");
            addCommonDynamicClass(scene, "sun.net.www.protocol.http.Handler");
            addCommonDynamicClass(scene, "sun.net.www.protocol.https.Handler");
            addCommonDynamicClass(scene, "sun.net.www.protocol.jar.Handler");
        }

        scene.loadNecessaryClasses();
        
        /*
        * This part should definitely appear after the call to
        * `Scene.loadNecessaryClasses()', since the latter may alter
        * the set of application classes by explicitly specifying
        * that some classes are library code (ignoring any previous
        * call to `setApplicationClass()').
        */

        classes.stream().filter((klass) -> isApplicationClass(sootParameters, klass)).forEachOrdered(SootClass::setApplicationClass);

        if (sootParameters._mode == SootParameters.Mode.FULL && !sootParameters._onlyApplicationClassesFactGen) {
            classes = new HashSet<>(scene.getClasses());
        }

        System.out.println("Total classes in Scene: " + classes.size());
        try {
            PackManager.v().retrieveAllSceneClassesBodies();
            System.out.println("Retrieved all bodies");
        }
        catch (Exception ex) {
            System.out.println("Not all bodies retrieved");
        }
        Database db = new Database(new File(sootParameters._outputDir), sootParameters._uniqueFacts);
        FactWriter writer = new FactWriter(db);
        ThreadFactory factory = new ThreadFactory(writer, sootParameters._ssa, sootParameters._generateJimple);
        Driver driver = new Driver(factory, classes.size(), sootParameters._generateJimple);

        classes.stream().filter(SootClass::isApplicationClass).forEachOrdered(writer::writeApplicationClass);

        // Read all stored properties files
        for (Map.Entry<String, Properties> entry : propertyProvider.getProperties().entrySet()) {
            String path = entry.getKey();
            Properties properties = entry.getValue();

            for (String propertyName : properties.stringPropertyNames()) {
                String propertyValue = properties.getProperty(propertyName);
                writer.writeProperty(path, propertyName, propertyValue);
            }
        }

        db.flush();

        if (sootParameters._android) {
            if (sootParameters._runFlowdroid) {
                driver.doAndroidInSequentialOrder(dummyMain, classes, writer, sootParameters._ssa);
                db.close();
                return;
            } else {
                String apkLocation = sootParameters._inputs.get(0);
                ProcessManifest processMan = new ProcessManifest(apkLocation);

                if (processMan.getApplicationName() != null)
                    writer.writeApplication(processMan.expandClassName(processMan.getApplicationName()));
                for (AXmlNode node : appActivities) {
                    writer.writeActivity(processMan.expandClassName(node.getAttribute("name").getValue().toString()));
                }

                for (AXmlNode node : appServices) {
                    writer.writeService(processMan.expandClassName(node.getAttribute("name").getValue().toString()));
                }

                for (AXmlNode node : appContentProviders) {
                    writer.writeContentProvider(processMan.expandClassName(node.getAttribute("name").getValue().toString()));
                }

                for (AXmlNode node : appBroadcastReceivers) {
                    writer.writeBroadcastReceiver(processMan.expandClassName(node.getAttribute("name").getValue().toString()));
                }

                for (Set<String> callBackMethods : appCallbackMethods.values()) {
                    for (String callbackMethod : callBackMethods) {
                        writer.writeCallbackMethod(callbackMethod);
                    }
                }

                for (Set<PossibleLayoutControl> possibleLayoutControls : appUserControls.values()) {
                    for (PossibleLayoutControl possibleLayoutControl : possibleLayoutControls) {
                        writer.writeLayoutControl(possibleLayoutControl.getID(), possibleLayoutControl.getViewClassName(), possibleLayoutControl.getParentID());
                        if (possibleLayoutControl.isSensitive()) {
                            writer.writeSensitiveLayoutControl(possibleLayoutControl.getID(), possibleLayoutControl.getViewClassName(), possibleLayoutControl.getParentID());
                        }
                    }
                }
            }
        }
        if (!sootParameters._noFacts) {

            scene.getOrMakeFastHierarchy();
            // avoids a concurrent modification exception, since we may
            // later be asking soot to add phantom classes to the scene's hierarchy
            driver.doInParallel(classes);
        }

        db.close();
    }

    private static void addCommonDynamicClass(Scene scene, String className) {
        if( SourceLocator.v().getClassSource(className) != null) {
            scene.addBasicClass(className);
        }
    }
}
