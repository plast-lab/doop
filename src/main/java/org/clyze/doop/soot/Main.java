package org.clyze.doop.soot;

import org.clyze.doop.common.Database;
import org.clyze.doop.soot.android.AndroidSupport;
import org.clyze.doop.util.filter.GlobClassFilter;
import org.clyze.utils.AARUtils;
import org.clyze.utils.Helper;
import org.objectweb.asm.ClassReader;
import soot.PackManager;
import soot.Scene;
import soot.SootClass;
import soot.SourceLocator;
import soot.SourceLocator.FoundFile;
import soot.options.Options;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.stream.Stream;

public class Main {

    private static int shift(String[] args, int index) throws DoopErrorCodeException {
        if(args.length == index + 1) {
            System.err.println("error: option " + args[index] + " requires an argument");
            throw new DoopErrorCodeException(9);
        }
        return index + 1;
    }

    private static boolean isApplicationClass(SootParameters sootParameters, SootClass klass) {
        sootParameters.applicationClassFilter = new GlobClassFilter(sootParameters.appRegex);

        return sootParameters.applicationClassFilter.matches(klass.getName());
    }

    public static void main(String[] args) throws Throwable {
        SootParameters sootParameters = new SootParameters();
        String extraSensitiveControls = "";
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
                        sootParameters._android = true;
                        sootParameters._androidJars = args[i];
                        break;
                    case "-i":
                        i = shift(args, i);
                        sootParameters._inputs.add(args[i]);
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
                            throw new DoopErrorCodeException(4);
                        } else if (!f.isDirectory()) {
                            System.err.println("Dependency folder " + folderName + " is not a directory");
                            throw new DoopErrorCodeException(5);
                        }
                        for (File file : Objects.requireNonNull(f.listFiles())) {
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
                    case "--fact-gen-cores":
                        i = shift(args, i);
                        try {
                            sootParameters._cores = new Integer(args[i]);
                        } catch (NumberFormatException nfe) {
                            System.out.println("Invalid cores argument: " + args[i]);
                        }
                        break;
                    case "--ignoreWrongStaticness":
                        sootParameters._ignoreWrongStaticness = true;
                        break;
                    case "--R-out-dir":
                        i = shift(args, i);
                        sootParameters._rOutDir = args[i];
                        break;
                    case "--extra-sensitive-controls":
                        i = shift(args, i);
                        extraSensitiveControls = args[i];
                        break;
                    case "--seed":
                        i = shift(args, i);
                        sootParameters._seed = args[i];
                        break;
                    case "--special-cs-methods":
                        i = shift(args, i);
                        sootParameters._specialCSMethods = args[i];
                        break;
                    case "-h":
                    case "--help":
                    case "-help":
                        System.err.println("\nusage: [options] file");
                        System.err.println("options:");
                        System.err.println("  --main <class>                        Specify the name of the main class");
                        System.err.println("  --ssa                                 Generate SSA facts, enabling flow-sensitive analysis");
                        System.err.println("  --full                                Generate facts by full transitive resolution");
                        System.err.println("  -d <directory>                        Specify where to generate csv fact files");
                        System.err.println("  -l <archive>                          Find classes in jar/zip archive");
                        System.err.println("  -lsystem                              Find classes in default system classes");
                        System.err.println("  --deps <directory>                    Add jars in this directory to the class lookup path");
                        System.err.println("  --only-application-classes-fact-gen   Generate facts only for application classes");
                        System.err.println("  --noFacts                             Don't generate facts (just empty files -- used for debugging)");
                        System.err.println("  --uniqueFacts                         Eliminate redundancy from facts");
                        System.err.println("  --ignoreWrongStaticness               Ignore 'wrong static-ness' errors in Soot.");
                        System.err.println("  --R-out-dir <directory>               Specify when to generate R code (when linking AAR inputs)");
                        System.err.println("  --extra-sensitive-controls <controls> A list of extra sensitive layout controls (format: \"id1,type1,parent_id1,id2,...\").");
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
                            throw new DoopErrorCodeException(6);
                        }
                        break;
                }
            }

            if(sootParameters._mode == null) {
                sootParameters._mode = SootParameters.Mode.INPUTS;
            }

            if (sootParameters._toStdout && !sootParameters._generateJimple) {
                System.err.println("error: --stdout must be used with --generate-jimple");
                throw new DoopErrorCodeException(7);
            }
            if (sootParameters._toStdout && sootParameters._outputDir != null) {
                System.err.println("error: --stdout and -d options are not compatible");
                throw new DoopErrorCodeException(2);
            }
            else if ((sootParameters._inputs.stream().filter(s -> s.endsWith(".apk") || s.endsWith(".aar")).count() > 0) &&
                    (!sootParameters._android)) {
                System.err.println("error: the --platform parameter is mandatory for .apk/.aar inputs");
                throw new DoopErrorCodeException(3);
            }
            else if (!sootParameters._toStdout && sootParameters._outputDir == null) {
                sootParameters._outputDir = System.getProperty("user.dir");
            }
            produceFacts(sootParameters, extraSensitiveControls);
        }
        catch(DoopErrorCodeException errCode) {
            System.err.println("Exiting with code " + errCode.getErrorCode());
            throw errCode;
        }
        catch(Exception exc) {
            exc.printStackTrace();
            throw exc;
        }
    }

    private static void produceFacts(SootParameters sootParameters, String extraSensitiveControls) throws Exception {
        Options.v().set_output_dir(sootParameters._outputDir);
        Options.v().setPhaseOption("jb", "use-original-names:true");

        if (sootParameters._ignoreWrongStaticness)
            Options.v().set_wrong_staticness(Options.wrong_staticness_ignore);

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
        Set<String> classesInApplicationJars = new HashSet<>();
        Map<String, Set<String>> classToArtifactMap = new HashMap<>();

        AndroidSupport android = null;

        // Set of temporary directories to be cleaned up after analysis ends.
        Set<String> tmpDirs = new HashSet<>();
        if (sootParameters._android) {
            if (sootParameters._inputs.size() > 1)
                System.err.println("\nWARNING -- Android mode: all inputs will be preprocessed but only " + sootParameters._inputs.get(0) + " will be considered as application file. The rest of the input files may be ignored by Soot.\n");
            Options.v().set_process_multiple_dex(true);
            Options.v().set_src_prec(Options.src_prec_apk);
            String rOutDir = sootParameters._rOutDir;
            android = new AndroidSupport(rOutDir, sootParameters, extraSensitiveControls);
            android.processInputs(propertyProvider, classesInApplicationJars, classToArtifactMap, sootParameters._androidJars, tmpDirs);
        } else {
            Options.v().set_src_prec(Options.src_prec_class);
            populateClassesInAppJar(sootParameters._inputs, sootParameters._libraries, classesInApplicationJars, classToArtifactMap, propertyProvider);
        }

        Scene scene = Scene.v();
        for (String input : sootParameters._inputs) {
            String inputFormat = input.endsWith(".jar")? "archive" : "file";
            System.out.println("Adding " + inputFormat + ": "  + input);

            addToSootClassPath(scene, input);
            if (sootParameters._android)
                break;
        }

        for (String lib : AARUtils.toJars(sootParameters._libraries, false, tmpDirs)) {
            System.out.println("Adding archive for resolving: " + lib);
            addToSootClassPath(scene, lib);
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
            if (android == null) {
                System.err.println("Internal error when adding Android classes.");
            } else {
                android.addClasses(classesInApplicationJars, classes, scene);
            }
        } else {
            addClasses(classesInApplicationJars, classes, scene);
            addBasicClasses(scene);

            System.out.println("Classes in input (application) jar(s): " + classesInApplicationJars.size());
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

        try {
            System.out.println("Total classes in Scene: " + classes.size());
            PackManager.v().retrieveAllSceneClassesBodies();
            System.out.println("Retrieved all bodies");
        }
        catch (Exception ex) {
            System.out.println("Not all bodies retrieved");
        }
        Database db = new Database(new File(sootParameters._outputDir), sootParameters._uniqueFacts);
        FactWriter writer = new FactWriter(db);
        ThreadFactory factory = new ThreadFactory(writer, sootParameters._ssa);
        Driver driver = new Driver(factory, classes.size(), sootParameters._generateJimple, sootParameters._cores);

        writePreliminaryFacts(classes, propertyProvider, classToArtifactMap, writer);

        db.flush();

        if (sootParameters._android) {
            if (android == null) {
                System.err.println("Internal error in Android handling.");
            } else {
                if (sootParameters._runFlowdroid) {
                    driver.doAndroidInSequentialOrder(android.getDummyMain(), classes, writer, sootParameters._ssa);
                    db.close();
                    return;
                } else {
                    android.writeComponents(writer);
                }
            }
        }
        if (!sootParameters._noFacts) {
            scene.getOrMakeFastHierarchy();
            // avoids a concurrent modification exception, since we may
            // later be asking soot to add phantom classes to the scene's hierarchy
            driver.doInParallel(classes);
            if (sootParameters._generateJimple) {
                Set<SootClass> jimpleClasses = new HashSet<>(classes);
                List<String> allClassNames = new ArrayList<>();
                for (String artifact : classToArtifactMap.keySet()) {
//                    if (!artifact.equals("rt.jar") && !artifact.equals("jce.jar") && !artifact.equals("jsse.jar") && !artifact.equals("android.jar"))
                        allClassNames.addAll(classToArtifactMap.get(artifact));
                }
                forceResolveClasses(allClassNames, jimpleClasses, scene);
                System.out.println("Total classes (application, dependencies and SDK) to generate Jimple for: " + jimpleClasses.size());
                driver.writeInParallel(jimpleClasses);
            }
        }

        if (sootParameters._seed != null) {
            try (Stream<String> stream = Files.lines(Paths.get(sootParameters._seed))) {
                stream.forEach(line -> processSeedFileLine(line, writer));
            }
        }

        if (sootParameters._specialCSMethods != null) {
            try (Stream<String> stream = Files.lines(Paths.get(sootParameters._specialCSMethods))) {
                stream.forEach(line -> processSpecialSensitivityMethodFileLine(line, writer));
            }
        }

        db.close();

        // Clean up any temporary directories used for AAR extraction.
        Helper.cleanUp(tmpDirs);
    }

    private static void processSeedFileLine(String line, FactWriter factWriter) {
        if (line.contains("(")) {
            factWriter.writeAndroidKeepMethod(line);
        }
        else if (line.contains(":")) {

        }
        else {
            factWriter.writeAndroidKeepClass(line);
        }
    }

    private static void processSpecialSensitivityMethodFileLine(String line, FactWriter factWriter) {
        if (line.contains(", ")) {
            factWriter.writeSpecialSensitivityMethod(line);
        }
    }

    private static boolean sootClassPathFirstElement = true;
    private static void addToSootClassPath(Scene scene, String input) {
        if (sootClassPathFirstElement) {
            scene.setSootClassPath(input);
            sootClassPathFirstElement = false;
        } else
            scene.extendSootClassPath(input);
    }

    private static void addCommonDynamicClass(Scene scene, String className) {
        if (SourceLocator.v().getClassSource(className) != null) {
            scene.addBasicClass(className);
        }
    }

    private static void populateClassToArtifactMap(Map<String, Set<String>> classToArtifactMap, String libraryFilename, String className) {
        Set<String> artifactClasses;
        if (!classToArtifactMap.containsKey(libraryFilename)) {
            artifactClasses = new HashSet<>();
            artifactClasses.add(className);
            classToArtifactMap.put(libraryFilename, artifactClasses);
        }
        else {
            artifactClasses = classToArtifactMap.get(libraryFilename);
            artifactClasses.add(className);
        }
    }

    public static void addClasses(Collection<String> classesToLoad, Collection<SootClass> loadedClasses, Scene scene) {
        for (String className : classesToLoad) {
            SootClass c = scene.loadClass(className, SootClass.BODIES);
            loadedClasses.add(c);
        }
    }

    private static void forceResolveClasses(Collection<String> classesToResolve, Collection<SootClass> resolvedClasses, Scene scene) {
        for (String className : classesToResolve) {
            scene.forceResolve(className, SootClass.BODIES);
            SootClass c = scene.loadClass(className, SootClass.BODIES);
            resolvedClasses.add(c);
        }
    }

    private static void addBasicClasses(Scene scene) {
        /*
         * Set resolution level for sun.net.www.protocol.ftp.FtpURLConnection
         * to 1 (HIERARCHY) before calling produceFacts(). The following line is necessary to avoid
         * a runtime exception when running soot with java 1.8, however it leads to different
         * input fact generation thus leading to different analysis results
         */
        scene.addBasicClass("sun.net.www.protocol.ftp.FtpURLConnection", SootClass.HIERARCHY);
        scene.addBasicClass("javax.crypto.extObjectInputStream");

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

    private static void writePreliminaryFacts(Set<SootClass> classes, PropertyProvider propertyProvider, Map<String, Set<String>> classToArtifactMap, FactWriter writer) {
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

        System.out.println("Class-to-Artifact map size: " + classToArtifactMap.size());
        for (String artifact : classToArtifactMap.keySet())
            for (String className : classToArtifactMap.get(artifact))
                writer.writeClassArtifact(artifact, className);
    }

    /**
     * Helper method to read classes and property files from JAR/AAR files.
     *
     * @param inputFilenames             the list of all input jar file names
     * @param classesInApplicationJar  the set to populate
     * @param propertyProvider         the provider to use for .properties files
     *
     * @return the name of the JAR file that was processed; this is
     * either the original first parameter, or the locally saved
     * classes.jar found in the .aar file (if such a file was given)
     *
     */
    public static void populateClassesInAppJar(List<String> inputFilenames,
                                               List<String> libraryFilenames,
                                               Set<String> classesInApplicationJar,
                                               Map<String, Set<String>> classToArtifactMap,
                                               PropertyProvider propertyProvider) throws Exception {

        for (String inputFilename : inputFilenames) {

            JarEntry entry;

            try (JarInputStream jin = new JarInputStream(new FileInputStream(inputFilename));
                 JarFile jarFile = new JarFile(inputFilename))
            {
                System.out.println("Processing application JAR: " + inputFilename);

                /* List all JAR entries */
                while ((entry = jin.getNextJarEntry()) != null) {
                    /* Skip directories */
                    if (entry.isDirectory())
                        continue;

                    String entryName = entry.getName();
                    if (entryName.endsWith(".class")) {
                        ClassReader reader = new ClassReader(jarFile.getInputStream(entry));
                        String className = reader.getClassName().replace("/", ".");
                        classesInApplicationJar.add(className);
                        populateClassToArtifactMap(classToArtifactMap, inputFilename.substring(inputFilename.lastIndexOf('/') + 1, inputFilename.length()), className);
                    } else if (entryName.endsWith(".properties")) {
                        propertyProvider.addProperties((new FoundFile(inputFilename, entryName)));
                    } /* Skip non-class files and non-property files */
                }
            }
        }

        for (String libraryFilename : libraryFilenames) {
            JarEntry entry;

            try (JarInputStream jin = new JarInputStream(new FileInputStream(libraryFilename));
                 JarFile jarFile = new JarFile(libraryFilename)) {

                System.out.println("Processing library JAR: " + libraryFilename);

                /* List all JAR entries */
                while ((entry = jin.getNextJarEntry()) != null) {
                    /* Skip directories */
                    if (entry.isDirectory())
                        continue;

                    String entryName = entry.getName();
                    if (entryName.endsWith(".class")) {
                        ClassReader reader = new ClassReader(jarFile.getInputStream(entry));
                        String className = reader.getClassName().replace("/", ".");
                        populateClassToArtifactMap(classToArtifactMap, libraryFilename.substring(libraryFilename.lastIndexOf('/') + 1, libraryFilename.length()), className);
                    } else if (entryName.endsWith(".properties")) {
                        propertyProvider.addProperties((new FoundFile(libraryFilename, entryName)));
                    } /* Skip non-class files and non-property files */
                }
            }
        }
    }
}
