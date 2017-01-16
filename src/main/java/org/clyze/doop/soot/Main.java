package org.clyze.doop.soot;

import org.clyze.doop.util.filter.ClassFilter;
import org.clyze.doop.util.filter.GlobClassFilter;
import soot.*;
import soot.jimple.infoflow.android.SetupApplication;
import soot.jimple.infoflow.android.axml.AXmlNode;
import soot.jimple.infoflow.android.manifest.ProcessManifest;
import soot.jimple.infoflow.android.resources.ARSCFileParser;
import soot.jimple.infoflow.android.resources.DirectLayoutFileParser;
import soot.jimple.infoflow.android.resources.PossibleLayoutControl;

import java.io.File;
import java.util.*;

import static soot.jimple.infoflow.android.InfoflowAndroidConfiguration.CallbackAnalyzer.Fast;

public class Main {

    private enum Mode {INPUTS, FULL}
    private static Mode _mode = null;
    private static List<String> _inputs = new ArrayList<>();
    private static List<String> _libraries = new ArrayList<>();
    private static String _outputDir = null;
    private static String _main = null;
    private static boolean _classicFactGen = false;
    private static boolean _ssa = false;
    private static boolean _android = false;
    private static String _androidJars = null;
    private static boolean _allowPhantom = false;
    private static boolean _onlyApplicationClassesFactGen = false;
    private static ClassFilter applicationClassFilter;
    private static String appRegex = "**";
    private static boolean _runFlowdroid = false;

    private static boolean _bytecode2jimple = false;
    private static boolean _toStdout = false;

    private static int shift(String[] args, int index) {
        if(args.length == index + 1) {
            System.err.println("error: option " + args[index] + " requires an argument");
            System.exit(1);
        }

        return index + 1;
    }

    private static boolean isApplicationClass(SootClass klass) {
        applicationClassFilter = new GlobClassFilter(appRegex);

        return applicationClassFilter.matches(klass.getName());
    }

    public static void main(String[] args) {
        try {
            if (args.length == 0) {
                System.err.println("usage: [options] file...");
                System.exit(0);
            }

            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--full":
                        if (_mode != null) {
                            System.err.println("error: duplicate mode argument");
                            System.exit(1);
                        }

                        _mode = Mode.FULL;
                        break;
                    case "-d":
                        i = shift(args, i);
                        _outputDir = args[i];
                        break;
                    case "--main":
                        i = shift(args, i);
                        _main = args[i];
                        break;
                    case "--ssa":
                        _ssa = true;
                        break;
                    case "--android-jars":
                        i = shift(args, i);
                        _allowPhantom = true;
                        _android = true;
                        _androidJars = args[i];
                        break;
                    case "-l":
                        i = shift(args, i);
                        _libraries.add(args[i]);
                        break;
                    case "-lsystem":
                        String javaHome = System.getProperty("java.home");
                        _libraries.add(javaHome + File.separator + "lib" + File.separator + "rt.jar");
                        _libraries.add(javaHome + File.separator + "lib" + File.separator + "jce.jar");
                        _libraries.add(javaHome + File.separator + "lib" + File.separator + "jsse.jar");
                        break;
                    case "--deps":
                        i = shift(args, i);
                        String folderName = args[i];
                        File f = new File(folderName);
                        if (!f.exists()) {
                            System.err.println("Dependency folder " + folderName + " does not exist");
                            System.exit(0);
                        } else if (!f.isDirectory()) {
                            System.err.println("Dependency folder " + folderName + " is not a directory");
                            System.exit(0);
                        }
                        for (File file : f.listFiles()) {
                            if (file.isFile() && file.getName().endsWith(".jar")) {
                                _libraries.add(file.getCanonicalPath());
                            }
                        }
                        break;
                    case "--application-regex":
                        i = shift(args, i);
                        appRegex = args[i];
                        break;
                    case "--allow-phantom":
                        _allowPhantom = true;
                        break;
                    case "--run-flowdroid":
                        _runFlowdroid = true;
                        break;
                    case "--only-application-classes-fact-gen":
                        _onlyApplicationClassesFactGen = true;
                        break;
                    case "--bytecode2jimple":
                        _bytecode2jimple = true;
                        break;
                    case "--stdout":
                        _toStdout = true;
                        break;
                    case "--sequential":
                        _classicFactGen = true;
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

                        System.err.println("  --bytecode2jimple                     Generate Jimple/Shimple files instead of facts");
                        System.err.println("  --stdout                              Write Jimple/Shimple to stdout");
                        System.exit(0);
                    case "--bytecode2jimpleHelp":
                        System.err.println("\nusage: [options] file");
                        System.err.println("options:");
                        System.err.println("  --ssa                                 Generate Shimple files (use SSA for variables)");
                        System.err.println("  --full                                Generate Jimple/Shimple files by full transitive resolution");
                        System.err.println("  --stdout                              Write Jimple/Shimple to stdout");
                        System.err.println("  -d <directory>                        Specify where to generate files");
                        System.err.println("  -l <archive>                          Find classes in jar/zip archive");
                        System.err.println("  -lsystem                              Find classes in default system classes");
                        System.err.println("  --android-jars <archive>              The main android library jar (for android apks). The same jar should be provided in the -l option");
                        System.exit(0);
                    default:
                        if (args[i].charAt(0) == '-') {
                            System.err.println("error: unrecognized option: " + args[i]);
                            System.exit(0);
                        } else {
                            _inputs.add(args[i]);
                        }
                        break;
                }
            }

            if(_mode == null) {
                _mode = Mode.INPUTS;
            }

            if (_toStdout && !_bytecode2jimple) {
                System.err.println("error: --stdout must be used with --bytecode2jimple");
                System.exit(1);
            }
            if (_toStdout && _outputDir != null) {
                System.err.println("error: --stdout and -d options are not compatible");
                System.exit(2);
            }
            else if ((_inputs.stream().filter(s -> s.endsWith(".apk")).count() > 0) &&
                    (!_android)) {
                System.err.println("error: the --platform parameter is mandatory for .apk inputs");
                System.exit(3);
            }
            else if (!_toStdout && _outputDir == null) {
                _outputDir = System.getProperty("user.dir");
            }

            produceFacts();
        }
        catch(Exception exc) {
            exc.printStackTrace();
            System.exit(1);
        }
    }

    private static void produceFacts() throws Exception {
        NoSearchingClassProvider classProvider = new NoSearchingClassProvider();
        DexClassProvider dexClassProvider = new DexClassProvider();
        SootMethod dummyMain = null;
        Set<String> apkClasses = null;

        Set<SootClass> classes = new HashSet<>();
        List<AXmlNode> appServices = null;
        List<AXmlNode> appActivities = null;
        List<AXmlNode> appContentProviders = null;
        List<AXmlNode> appBroadcastReceivers = null;
        Map<String, Set<String>> appCallbackMethods = null;
        Map<String, Set<PossibleLayoutControl>> appUserControls = null;

        if (_android) {
            String apkLocation = _inputs.get(0);
            SetupApplication app = new SetupApplication(_androidJars, apkLocation);
            soot.options.Options.v().set_process_multiple_dex(true);

            apkClasses = new HashSet<>();
            apkClasses.addAll(SourceLocator.v().getClassesUnder(apkLocation));

            System.out.println("Classes found  in apk: " + apkClasses.size());


            if (_runFlowdroid) {
                app.getConfig().setCallbackAnalyzer(Fast);
                app.calculateSourcesSinksEntrypoints("SourcesAndSinks.txt");
                dummyMain = app.getDummyMainMethod();
                if (dummyMain == null) {
                    throw new RuntimeException("Dummy main null");
                }
            }
            else {
                ProcessManifest processMan = new ProcessManifest(apkLocation);
                String appPackageName = processMan.getPackageName();
                ARSCFileParser resParser = new ARSCFileParser();
                resParser.parse(apkLocation);
                List<ARSCFileParser.ResPackage> resourcePackages = resParser.getPackages();
                DirectLayoutFileParser lfp = new DirectLayoutFileParser(appPackageName, resParser);
                lfp.registerLayoutFilesDirect(apkLocation);
                lfp.parseLayoutFileDirect(apkLocation);

                // now collect the facts we need
                Set<String> appEntrypoints = processMan.getEntryPointClasses();
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

        }
        else {
            for (String arg : _inputs) {
                if (arg.endsWith(".jar") || arg.endsWith(".zip")) {
                    System.out.println("Adding archive: " + arg);
                    classProvider.addArchive(new File(arg));
                } else {
                    System.out.println("Adding file: " + arg);
                    classProvider.addClass(new File(arg));
                }
            }
        }
        Scene scene = Scene.v();
        for (String lib : _libraries) {
            System.out.println("Adding archive for resolving: " + lib);

            File libraryFile = new File(lib);

            if (!libraryFile.exists()) {
                System.err.println("Library file does not exist: " + libraryFile);
            } else {
                classProvider.addArchiveForResolving(libraryFile);
            }
        }

        List<ClassProvider> providersList = new ArrayList<>();

        if (_android)
            providersList.add(dexClassProvider);
        providersList.add(classProvider);
        soot.SourceLocator.v().setClassProviders(providersList);

        if(_main != null) {
            soot.options.Options.v().set_main_class(_main);
        }

        if(_mode == Mode.FULL) {
            soot.options.Options.v().set_full_resolver(true);
        }

        if(_allowPhantom) {
            soot.options.Options.v().set_allow_phantom_refs(true);
        }

        soot.options.Options.v().set_process_multiple_dex(true);
        soot.options.Options.v().setPhaseOption("jb", "use-original-names:true");
        soot.options.Options.v().setPhaseOption("jb.lp", "enabled:false");
        soot.options.Options.v().set_keep_line_number(true);


        if (_android) {
            String libraryClassPath = "";
            for(String library : _libraries) {
                libraryClassPath += File.pathSeparator + library;
            }
            scene.setSootClassPath(_inputs.get(0) + libraryClassPath);

            for (String className : apkClasses) {
                SourceLocator.v().getClassSource(className);
                scene.addBasicClass(className, SootClass.BODIES);
                classes.add(scene.forceResolve(className, SootClass.BODIES));
            }
        }

        for (String className : classProvider.getClassNames()) {
            scene.loadClass(className, SootClass.SIGNATURES);
            classes.add(scene.loadClass(className, SootClass.BODIES));
        }

        if (!_android) {
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
            addCommonDynamicClass(scene, classProvider, "java.io.UnixFileSystem");
            addCommonDynamicClass(scene, classProvider, "java.io.WinNTFileSystem");
            addCommonDynamicClass(scene, classProvider, "java.io.Win32FileSystem");

            /* java.net.URL loads handlers dynamically */
            addCommonDynamicClass(scene, classProvider, "sun.net.www.protocol.file.Handler");
            addCommonDynamicClass(scene, classProvider, "sun.net.www.protocol.ftp.Handler");
            addCommonDynamicClass(scene, classProvider, "sun.net.www.protocol.http.Handler");
            addCommonDynamicClass(scene, classProvider, "sun.net.www.protocol.https.Handler");
            addCommonDynamicClass(scene, classProvider, "sun.net.www.protocol.jar.Handler");
        }

        scene.loadNecessaryClasses();
        
        /*
        * This part should definitely appear after the call to
        * `Scene.loadNecessaryClasses()', since the latter may alter
        * the set of application classes by explicitly specifying
        * that some classes are library code (ignoring any previous
        * call to `setApplicationClass()').
        */

        classes.stream().filter(Main::isApplicationClass).forEachOrdered(SootClass::setApplicationClass);

        if(_mode == Mode.FULL && !_onlyApplicationClassesFactGen) {
            classes = new HashSet<>(scene.getClasses());
        }

        System.out.println("Total classes in Scene: " + classes.size());

        if (_bytecode2jimple) {
            ThreadFactory factory = new ThreadFactory(_ssa, _toStdout, _outputDir);
            Driver driver = new Driver(factory, _ssa, classes.size());

            driver.doInSequentialOrder(classes);
        }
        else {
            Database db = new Database(new File(_outputDir));
            FactWriter writer = new FactWriter(db);
            ThreadFactory factory = new ThreadFactory(writer, _ssa);
            Driver driver = new Driver(factory, _ssa, classes.size());

            classes.stream().filter(SootClass::isApplicationClass).forEachOrdered(writer::writeApplicationClass);

            // Read all stored properties files
            for (Map.Entry<String,Properties> entry : classProvider.getProperties().entrySet()) {
                String path = entry.getKey();
                Properties properties = entry.getValue();

                for (String propertyName : properties.stringPropertyNames()) {
                    String propertyValue = properties.getProperty(propertyName);
                    writer.writeProperty(path, propertyName, propertyValue);
                }
            }

            db.flush();

            if (_android) {
                if (_runFlowdroid) {
                    driver.doAndroidInSequentialOrder(dummyMain, classes, writer, _ssa);
                    db.close();
                    return;
                }
                else {
                    for (AXmlNode node : appActivities) {
                        writer.writeActivity(node.getAttribute("name").getValue().toString());
                    }

                    for (AXmlNode node : appServices) {
                        writer.writeService(node.getAttribute("name").getValue().toString());
                    }

                    for (AXmlNode node : appContentProviders) {
                        writer.writeContentProvider(node.getAttribute("name").getValue().toString());
                    }

                    for (AXmlNode node : appBroadcastReceivers) {
                        writer.writeBroadcastReceiver(node.getAttribute("name").getValue().toString());
                    }

                    for (Set<String> callBackMethods : appCallbackMethods.values()) {
                        for (String callbackMethod : callBackMethods) {
                            writer.writeCallbackMethod(callbackMethod);
                        }
                    }

                    for (Set<PossibleLayoutControl> possibleLayoutControls : appUserControls.values()) {
                        for (PossibleLayoutControl possibleLayoutControl : possibleLayoutControls) {
                            writer.writeLayoutControl(possibleLayoutControl.getID(), possibleLayoutControl.getViewClassName(), possibleLayoutControl.getParentID());
                        }
                    }
                }
            }
            if (_classicFactGen)
                driver.doInSequentialOrder(classes);
            else {
                scene.getOrMakeFastHierarchy();
                // avoids a concurrent modification exception, since we may
                // later be asking soot to add phantom classes to the scene's hierarchy
                driver.doInParallel(classes);
            }
            db.close();
        }
    }

    private static void addCommonDynamicClass(Scene scene, ClassProvider provider, String className) {
        if(provider.find(className) != null) {
            scene.addBasicClass(className);
        }
    }
}
