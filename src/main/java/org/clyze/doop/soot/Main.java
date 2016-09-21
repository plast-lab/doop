package org.clyze.doop.soot;

import org.clyze.doop.util.filter.ClassFilter;
import org.clyze.doop.util.filter.GlobClassFilter;
import soot.ClassProvider;
import soot.Scene;
import soot.SootClass;

import java.io.File;
import java.util.*;

public class Main {

    public static enum Mode {INPUTS, FULL;}
    private static Mode _mode = null;
    private static List<String> _inputs = new ArrayList<String>();
    private static List<String> _libraries = new ArrayList<String>();
    private static String _outputDir = null;
    private static String _main = null;
    private static boolean _ssa = false;
    private static boolean _allowPhantom = false;
    private static boolean _useOriginalNames = false;
    private static boolean _keepLineNumber = false;
    private static boolean _onlyApplicationClassesFactGen = false;
    private static ClassFilter applicationClassFilter;
    private static String appRegex = "**";

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
                if (args[i].equals("--full")) {
                    if( _mode != null) {
                        System.err.println("error: duplicate mode argument");
                        System.exit(1);
                    }

                    _mode = Mode.FULL;
                }
                else if (args[i].equals("-d")) {
                    i = shift(args, i);
                    _outputDir = args[i];
                }
                else if (args[i].equals("--main")) {
                    i = shift(args, i);
                    _main = args[i];
                }
                else if (args[i].equals("--ssa")) {
                    _ssa = true;
                }
                else if (args[i].equals("-l")) {
                    i = shift(args, i);
                    _libraries.add(args[i]);
                }
                else if (args[i].equals("-lsystem")) {
                    String javaHome = System.getProperty("java.home");
                    _libraries.add(javaHome + File.separator + "lib" + File.separator + "rt.jar");
                    _libraries.add(javaHome + File.separator + "lib" + File.separator + "jce.jar");
                    _libraries.add(javaHome + File.separator + "lib" + File.separator + "jsse.jar");
                }
                else if (args[i].equals("--deps")) {
                    i = shift(args, i);
                    String folderName = args[i];
                    File f = new File(folderName);
                    if (!f.exists()) {
                        System.err.println("Dependency folder " + folderName + " does not exist");
                        System.exit(0);
                    }
                    else if (!f.isDirectory()) {
                        System.err.println("Dependency folder " + folderName + " is not a directory");
                        System.exit(0);
                    }
                    for (File file : f.listFiles()) {
                        if (file.isFile() && file.getName().endsWith(".jar")) {
                            _libraries.add(file.getCanonicalPath());
                        }
                    }
                }
                else if (args[i].equals("--application-regex")) {
                    i = shift(args, i);
                    appRegex = args[i];
                }
                else if (args[i].equals("--allow-phantom")) {
                    _allowPhantom = true;
                }
                else if (args[i].equals("--use-original-names")) {
                    _useOriginalNames = true;
                }
                else if (args[i].equals("--only-application-classes-fact-gen")) {
                    _onlyApplicationClassesFactGen = true;
                }
                else if (args[i].equals("--keep-line-number")) {
                    _keepLineNumber = true;
                }

                else if (args[i].equals("--bytecode2jimple")) {
                    _bytecode2jimple = true;
                }
                else if (args[i].equals("--stdout")) {
                    _toStdout = true;
                }
                else if (args[i].equals("-h") || args[i].equals("--help") || args[i].equals("-help")) {
                    System.err.println("usage: [options] file");
                    System.err.println("options:");
                    System.err.println("  --main <class>                        Specify the main name of the main class");
                    System.err.println("  --ssa                                 Generate SSA facts, enabling flow-sensitive analysis");
                    System.err.println("  --full                                Generate facts by full transitive resolution");
                    System.err.println("  -d <directory>                        Specify where to generate csv fact files.");
                    System.err.println("  -l <archive>                          Find classes in jar/zip archive.");
                    System.err.println("  -lsystem                              Find classes in default system classes.");
                    System.err.println("  --deps <directory>                    Add jars in this directory to the class lookup path");
                    System.err.println("  --use-original-names                  Use original (source code) local variable names");
                    System.err.println("  --only-application-classes-fact-gen   Generate facts only for application classes");
                    System.err.println("  --keep-line-number                    Keep line number information for statements");

                    System.err.println("  --bytecode2jimple                     Generate Jimple/Shimple files instead of facts");
                    System.err.println("  --stdout                              Write Jimple/Shimple to stdout");

                    System.err.println("  -h, -help                             Print this help message.");
                    System.exit(0);
                }
                else if (args[i].equals("--bytecode2jimpleHelp")) {
                    System.err.println("usage: [options] file");
                    System.err.println("options:");
                    System.err.println("  --ssa                                 Generate SSA facts, enabling flow-sensitive analysis");
                    System.err.println("  --full                                Generate facts by full transitive resolution");
                    System.err.println("  --stdout                              Write Jimple/Shimple to stdout");
                    System.err.println("  -d <directory>                        Specify where to generate csv fact files.");
                    System.err.println("  -l <archive>                          Find classes in jar/zip archive.");
                    System.err.println("  -lsystem                              Find classes in default system classes.");
                    System.exit(0);
                }
                else {
                    if (args[i].charAt(0) == '-') {
                        System.err.println("error: unrecognized option: " + args[i]);
                        System.exit(0);
                    }
                    else {
                        _inputs.add(args[i]);
                    }
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
            else if (!_toStdout && _outputDir == null) {
                _outputDir = System.getProperty("user.dir");
            }

            /*
             * Set resolution level for sun.net.www.protocol.ftp.FtpURLConnection
             * to 1 (HIERARCHY) before calling run(). The following line is necessary to avoid
             * a runtime exception when running soot with java 1.8, however it leads to different
             * input fact generation thus leading to different analysis results
             */
            Scene.v().addBasicClass("sun.net.www.protocol.ftp.FtpURLConnection", 1);
            run();
        }
        catch(Exception exc) {
            exc.printStackTrace();
            System.exit(1);
        }
    }

    private static void run() throws Exception {
        NoSearchingClassProvider provider = new NoSearchingClassProvider();

        for(String arg : _inputs) {
            if(arg.endsWith(".jar") || arg.endsWith(".zip")) {
                System.out.println("Adding archive: " + arg);
                provider.addArchive(new File(arg));
            }
            else {
                System.out.println("Adding file: " + arg);
                provider.addClass(new File(arg));
            }
        }

        for(String lib: _libraries) {
            System.out.println("Adding archive for resolving: " + lib);

            File libraryFile = new File(lib);

            if (!libraryFile.exists()) {
                System.err.println("Library file does not exist: " + libraryFile);
            }
            else {
                provider.addArchiveForResolving(libraryFile);
            }
        }

        soot.SourceLocator.v().setClassProviders(Collections.singletonList((ClassProvider) provider));
        Scene scene = Scene.v();
        if(_main != null) {
            soot.options.Options.v().set_main_class(_main);
        }

        if(_mode == Mode.FULL) {
            soot.options.Options.v().set_full_resolver(true);
        }

        if(_allowPhantom) {
            soot.options.Options.v().set_allow_phantom_refs(true);
        }

        if (_useOriginalNames) {
            soot.options.Options.v().setPhaseOption("jb", "use-original-names:true");
        }

        if (_keepLineNumber) {
            soot.options.Options.v().set_keep_line_number(true);
        }

        List<SootClass> classes = new ArrayList<>();
        for(String className : provider.getClassNames()) {
            scene.loadClass(className, SootClass.SIGNATURES);
            SootClass c = scene.loadClass(className, SootClass.BODIES);

            classes.add(c);
        }


        /*
         * For simulating the FileSystem class, we need the implementation
         * of the FileSystem, but the classes are not loaded automatically
         * due to the indirection via native code.
         */
        addCommonDynamicClass(scene, provider, "java.io.UnixFileSystem");
        addCommonDynamicClass(scene, provider, "java.io.WinNTFileSystem");
        addCommonDynamicClass(scene, provider, "java.io.Win32FileSystem");

        /* java.net.URL loads handlers dynamically */
        addCommonDynamicClass(scene, provider, "sun.net.www.protocol.file.Handler");
        addCommonDynamicClass(scene, provider, "sun.net.www.protocol.ftp.Handler");
        addCommonDynamicClass(scene, provider, "sun.net.www.protocol.http.Handler");
        addCommonDynamicClass(scene, provider, "sun.net.www.protocol.https.Handler");
        addCommonDynamicClass(scene, provider, "sun.net.www.protocol.jar.Handler");

        scene.loadNecessaryClasses();


       /*
        * This part should definitely appear after the call to
        * `Scene.loadNecessaryClasses()', since the latter may alter
        * the set of application classes by explicitly specifying
        * that some classes are library code (ignoring any previous
        * call to `setApplicationClass()').
        */

        for(SootClass c : classes) {
            if (isApplicationClass(c))
                c.setApplicationClass();
        }

        if(_mode == Mode.FULL && !_onlyApplicationClassesFactGen) {
            classes = new ArrayList<>(scene.getClasses());
        }

        if (_bytecode2jimple) {
            ThreadFactory factory = new ThreadFactory(_ssa, _toStdout, _outputDir);
            Driver driver = new Driver(factory, _ssa, classes.size());

            driver.doInParallel(classes);
        }
        else {
            Database db = new CSVDatabase(new File(_outputDir));
            FactWriter writer = new FactWriter(db);
            ThreadFactory factory = new ThreadFactory(writer, _ssa);
            Driver driver = new Driver(factory, _ssa, classes.size());

            for(SootClass c : classes) {
                if (c.isApplicationClass())
                    writer.writeApplicationClass(c);
            }

            // Read all stored properties files
            for (Map.Entry<String,Properties> entry : provider.getProperties().entrySet()) {
                String path = entry.getKey();
                Properties properties = entry.getValue();

                for (String propertyName : properties.stringPropertyNames()) {
                    String propertyValue = properties.getProperty(propertyName);

                    writer.writeProperty(path, propertyName, propertyValue);
                }
            }

            db.flush();

            driver.doInParallel(classes);

            db.close();
        }
    }

    private static void addCommonDynamicClass(Scene scene, ClassProvider provider, String className) {
        if(provider.find(className) != null) {
            scene.addBasicClass(className);
        }
    }
}
