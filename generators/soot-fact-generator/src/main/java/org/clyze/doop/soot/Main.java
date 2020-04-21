package org.clyze.doop.soot;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.log4j.Logger;
import org.clyze.doop.common.ArtifactEntry;
import org.clyze.doop.common.ArtifactScanner;
import org.clyze.doop.common.Database;
import org.clyze.doop.common.DatabaseConnector;
import org.clyze.doop.common.DoopErrorCodeException;
import org.clyze.doop.soot.android.AndroidSupport_Soot;
import org.clyze.scanner.BinaryAnalysis;
import org.clyze.scanner.NativeScanner;
import org.clyze.utils.AARUtils;
import org.clyze.utils.JHelper;
import org.xmlpull.v1.XmlPullParserException;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.options.Options;

import static org.clyze.doop.common.FrontEndLogger.*;

public class Main {

    private static Logger logger;
    private static final String debug = System.getenv("SOOT_DEBUG");

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("usage: [options] file...");
            SootParameters.showHelp();
            throw new DoopErrorCodeException(0);
        }

        try {
            produceFacts(args);
        } catch (Exception ex) {
            // We assume Doop exceptions have already printed
            // something to the standard error output.
            if (!(ex instanceof DoopErrorCodeException))
                ex.printStackTrace();
            throw ex;
        }
    }

    private static void produceFacts(String[] args) throws Exception {
        SootParameters sootParameters = new SootParameters();
        sootParameters.initFromArgs(args);
        String outDir = sootParameters.getOutputDir();

        try {
            String logDir = sootParameters.getLogDir();
            JHelper.tryInitLogging("DEBUG", logDir, true, "soot-fact-generator");
            logger = Logger.getLogger(Main.class);
            logger.info("Logging initialized, using directory: " + logDir);
        } catch (IOException ex) {
            System.err.println("WARNING: could not initialize logging");
            throw new DoopErrorCodeException(18);
        }

        checkJVMArgs();

        DoopAddons.initReflectiveAccess();

        Options.v().set_output_dir(outDir);
        Options.v().setPhaseOption("jb", "use-original-names:true");
        Options.v().setPhaseOption("jb", "model-lambdametafactory:false");

        if (sootParameters._ignoreWrongStaticness || sootParameters._ignoreFactGenErrors)
            Options.v().set_wrong_staticness(Options.wrong_staticness_ignore);

        if (sootParameters._ssa) {
            Options.v().set_via_shimple(true);
            Options.v().set_output_format(Options.output_format_shimple);
        } else {
            logWarn(logger, "WARNING: SSA not enabled, generating Jimple instead of Shimple");
            Options.v().set_output_format(Options.output_format_jimple);
        }

        //soot.options.Options.v().set_drop_bodies_after_load(true);
        Options.v().set_keep_line_number(true);

        BasicJavaSupport_Soot java = new BasicJavaSupport_Soot(sootParameters, new ArtifactScanner());

        // Set of temporary directories to be cleaned up after analysis ends.
        Set<String> tmpDirs = new HashSet<>();

        // Set up Soot options that depend on target platform (Android/Java).
        AndroidSupport_Soot android;
        if (sootParameters._dex) {
            System.out.println("Running in mixed Soot/Dex mode.");
            android = null;
        } else if (sootParameters._android) {
            if (sootParameters.getInputs().size() > 1)
                logWarn(logger, "WARNING: Android mode: all inputs will be preprocessed but only " + sootParameters.getInputs().get(0) + " will be considered as application file. The rest of the input files may be ignored by Soot.\n");
            Options.v().set_process_multiple_dex(true);
            Options.v().set_src_prec(Options.src_prec_apk);
            if (sootParameters._androidJars == null)
                logWarn(logger, "WARNING: missing --android-jars option.");
            else
                Options.v().set_android_jars(sootParameters._androidJars);
            android = new AndroidSupport_Soot(sootParameters, java);
        } else {
            Options.v().set_src_prec(Options.src_prec_class);
            android = null;
        }

        boolean writeFacts = !sootParameters.noFacts();
        try (Database db = new Database(outDir, writeFacts)) {
            java.preprocessInputs(db);

            AtomicInteger errors = new AtomicInteger(0);
            if (android != null)
                java.getExecutor().execute(() -> android.processInputs(tmpDirs));

            Scene scene = Scene.v();
            SootData sootData = new SootData();
            java.getExecutor().execute(() -> {
                    try {
                        invokeSoot(sootParameters, db, tmpDirs, sootData, java, android, scene, writeFacts);
                    } catch (Throwable t) {
                        t.printStackTrace();
                        errors.incrementAndGet();
                    }});

            // Wait for async tasks to finish for next steps such as
            // IR generation (needs information from previous steps).
            SootDriver.waitForExecutorShutdown(java.getExecutor());
            int numErrors = errors.intValue();
            if (numErrors != 0)
                throw new DoopErrorCodeException(35, "Fact generation failed with " + numErrors + " errors.");

            if (writeFacts && sootParameters._scanNativeCode) {
                DatabaseConnector dbc = new DatabaseConnector(db);
                scanNativeInputs(dbc, sootParameters._radare, sootParameters._preciseNativeStrings, sootData.writer.getMethodStrings(), sootParameters.getInputs());
            }

            if (sootParameters._generateJimple)
                generateIR(java, scene, sootData.classes, sootData.driver, outDir);

            System.err.println("Methods without active bodies encountered (and reset): " + FactGenerator.methodsWithoutActiveBodies.get());

            if (sootParameters._lowMem) {
                System.out.println("Releasing Soot structures...");
                for (SootClass cl : scene.getClasses())
                    for (SootMethod m : cl.getMethods())
                        if (m.hasActiveBody())
                            m.setActiveBody(null);
                System.gc();
                System.out.println("Done.");
            }
        } finally {
            // Clean up any temporary directories used for AAR extraction.
            JHelper.cleanUp(tmpDirs);
        }
    }

    /**
     * This is the part of Soot that can run in parallel with other
     * (pre)processing tasks.
     */
    private static void invokeSoot(SootParameters sootParameters, Database db, Set<String> tmpDirs, SootData sootData, BasicJavaSupport_Soot java, AndroidSupport_Soot android, Scene scene, boolean writeFacts) throws DoopErrorCodeException, IOException {
        if (debug != null)
            showPacks();

        DoopConventions.setSeparator();
        List<String> inputs = sootParameters.getInputs();
        for (String input : inputs) {
            String inputFormat = input.endsWith(".jar")? "archive" : "file";
            System.out.println("Adding " + inputFormat + ": "  + input);

            addToSootClassPath(scene, input);
            if (sootParameters._android) {
                if (inputs.size() > 1)
                    logWarn(logger, "WARNING: skipping rest of inputs");
                break;
            }
        }

        List<String> allLibs = sootParameters.getDependenciesAndPlatformLibs();
        for (String lib : AARUtils.toJars(allLibs, false, tmpDirs)) {
            System.out.println("Adding archive for resolving: " + lib);
            addToSootClassPath(scene, lib);
        }

        if (sootParameters._main != null)
            Options.v().set_main_class(sootParameters._main);

        if (sootParameters._mode == SootParameters.Mode.FULL)
            Options.v().set_full_resolver(true);

        if (sootParameters._allowPhantom)
            Options.v().set_allow_phantom_refs(true);

        Set<SootClass> classes = new HashSet<>();
        ClassAdder classAdder = (android != null) ? android : java;
        if (sootParameters._factsSubSet == SootParameters.FactsSubSet.APP)
            classAdder.addAppClasses(classes, scene);
        else if (sootParameters._factsSubSet == SootParameters.FactsSubSet.APP_N_DEPS) {
            classAdder.addAppClasses(classes, scene);
            classAdder.addDepClasses(classes, scene);
        } else if (sootParameters._factsSubSet == SootParameters.FactsSubSet.PLATFORM)
            classAdder.addLibClasses(classes, scene);
        else
            classAdder.addAppClasses(classes, scene);

        for (String extraClass : sootParameters.getExtraClassesToResolve()) {
            System.out.println("Marking class to resolve: " + extraClass);
            scene.addBasicClass(extraClass, SootClass.BODIES);
        }

        scene.loadNecessaryClasses();

        /*
         * This part should definitely appear after the call to
         * `Scene.loadNecessaryClasses()', since the latter may alter
         * the set of application classes by explicitly specifying
         * that some classes are library code (ignoring any previous
         * call to `setApplicationClass()').
         */

        classes.stream().filter(sootParameters::isApplicationClass).forEachOrdered(SootClass::setApplicationClass);

        classes = new HashSet<>(scene.getClasses());
        System.out.println("Total classes in Scene: " + classes.size());

        // Skip "retrieve all bodies" step for Android apps.
        if (android == null) {
            long time1 = System.currentTimeMillis();
            try {
                DoopAddons.retrieveAllSceneClassesBodies(sootParameters._cores);
                // The call below has a problem (only retrieves app method bodies).
                // DoopAddons.retrieveAllBodies();
                long time2 = System.currentTimeMillis();
                System.out.println("Retrieved all bodies (time: " + ((time2 - time1)/1000) + ")");
            } catch (Exception ex) {
                System.err.println("Error: not all bodies retrieved.");
                ex.printStackTrace();
            }
        }

        boolean reportPhantoms = sootParameters._reportPhantoms;
        boolean moreStrings = sootParameters._extractMoreStrings;
        boolean artifacts = sootParameters._writeArtifactsMap;
        Representation rep = new Representation();

        FactWriter writer = new FactWriter(db, sootParameters, artifacts, rep, reportPhantoms);
        SootDriver driver = new SootDriver(classes.size(), sootParameters._cores, sootParameters._ignoreFactGenErrors, writer, sootParameters);

        if (writeFacts) {

            writer.writePreliminaryFacts(classes, java);
            db.flush();

            if (android != null) {
                android.generateFactsForXML(db);
                if (sootParameters._legacyAndroidProcessing)
                    android.writeComponents(writer);
            }

            scene.getOrMakeFastHierarchy();

            // avoids a concurrent modification exception, since we may
            // later be asking soot to add phantom classes to the scene's hierarchy
            driver.generateInParallel(classes);

            logDebug(logger, "Checking class heaps for missing types...");
            Collection<String> unrecorded = new ClassHeapFinder().getUnrecordedTypes(classes);
            if (unrecorded.size() > 0) {
                // If option is set, fail and notify caller that fact generation
                // must run again with these classes added.
                String outFile = sootParameters._missingClassesOut;
                if (outFile != null) {
                    try (FileWriter fWriter = new FileWriter(new File(outFile))) {
                        unrecorded.forEach(s -> {
                                try {
                                    fWriter.write(s + '\n');
                                } catch (IOException ex) {
                                    System.err.println("ERROR: " + ex.getMessage());
                                }});
                    }
                    logError(logger, "ERROR: some classes were not resolved (see " + outFile + "), restarting fact generation: " + Arrays.toString(unrecorded.toArray()));
                } else
                    logWarn(logger, "WARNING: some classes were not resolved, consider using thorough fact generation or adding them manually via --also-resolve: " + Arrays.toString(unrecorded.toArray()));
            }

            writer.writeLastFacts(java);
        }

        // Communicate data structures to next stages of the pipeline.
        sootData.classes = classes;
        sootData.driver = driver;
        sootData.writer = writer;
    }

    private static boolean sootClassPathFirstElement = true;
    private static void addToSootClassPath(Scene scene, String input) {
        if (input.endsWith(".class"))
            System.err.println("WARNING: bare input class may not be resolved correctly, should be repackaged as a .jar: " + input);

        if (sootClassPathFirstElement) {
            scene.setSootClassPath(input);
            sootClassPathFirstElement = false;
        } else
            scene.extendSootClassPath(input);
    }

    private static void forceResolveClasses(Iterable<String> classesToResolve, Collection<SootClass> resolvedClasses, Scene scene) {
        for (String className : classesToResolve) {
            scene.forceResolve(className, SootClass.BODIES);
            SootClass c = scene.loadClass(className, SootClass.BODIES);
            resolvedClasses.add(c);
        }
    }

    public static class Standalone {
        public static void main(String[] args) throws Exception {
            try {
                Main.main(args);
            } catch (Exception e) {
                boolean normalExit = (e instanceof DoopErrorCodeException) && (((DoopErrorCodeException)e).getErrorCode() == 0);
                if (!normalExit)
                    throw e;
            }
        }
    }

    private static void showPacks() {
        for (soot.Pack pack : soot.PackManager.v().allPacks())
            System.out.println("Pack: " + pack.getPhaseName());
    }

    /**
     * Checks that the JVM arguments contain sane defaults. Also
     * prints the arguments when the environment variable SOOT_DEBUG
     * is set.
     */
    private static void checkJVMArgs() {
        final String UTF8_ENCODING = "-Dfile.encoding=UTF-8";

        RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
        boolean utf8 = false;
        for (String arg : runtimeMxBean.getInputArguments()) {
            if (debug != null)
                System.err.println("Soot front end argument: " + arg);
            if (arg.contains(UTF8_ENCODING))
                utf8 = true;
        }
        if (!utf8)
            logWarn(logger, "WARNING: 'file.encoding' property missing or not UTF8, please pass: " + UTF8_ENCODING);
    }

    /**
     * Generate Jimple/Shimple for the classes loaded into Soot.
     *  @param java             the Java platform support object
     * @param scene            the Soot scene
     * @param classes          the loaded classes
     * @param driver           the driver to use for parallelism
     * @param outDir           the (parent) output directory to use
     */
    private static void generateIR(BasicJavaSupport_Soot java, Scene scene,
                                   Set<SootClass> classes, SootDriver driver,
                                   String outDir) throws DoopErrorCodeException {
        Set<SootClass> jimpleClasses = new HashSet<>(classes);
        Collection<String> allClassNames = new ArrayList<>();
        Map<String, Set<ArtifactEntry>> artifactToClassMap = java.getArtifactScanner().getArtifactToClassMap();
        for (String artifact : artifactToClassMap.keySet()) {
            //                    if (!artifact.equals("rt.jar") && !artifact.equals("jce.jar") && !artifact.equals("jsse.jar") && !artifact.equals("android.jar"))
            Set<String> artEntries = ArtifactEntry.toClassNames(artifactToClassMap.get(artifact));
            allClassNames.addAll(artEntries);
        }
        forceResolveClasses(allClassNames, jimpleClasses, scene);
        System.out.println("Total classes (application, dependencies and SDK) to generate Jimple for: " + jimpleClasses.size());

        // Write classes, following package hierarchy.
        Options.v().set_output_dir(DoopConventions.jimpleDir(outDir));
        boolean structured = DoopAddons.checkSetHierarchyDirs();
        driver.writeInParallel(jimpleClasses);
        if (!structured)
            DoopAddons.structureJimpleFiles(outDir);
        // Revert to standard output dir for the rest of the code.
        Options.v().set_output_dir(outDir);
    }

    public static void scanNativeInputs(DatabaseConnector dbc,
                                        boolean useRadare,
                                        boolean preciseNativeStrings,
                                        Set<String> methodStrings,
                                        Iterable<String> inputs) {
        final boolean demangle = false;
        final boolean truncateAddresses = true;
        final NativeScanner scanner = new NativeScanner(methodStrings);

        ArtifactScanner.EntryProcessor gProc = (file, entry, entryName) -> {
            boolean isSO = entryName.endsWith(".so");
            boolean isDylib = entryName.endsWith(".dylib");
            boolean isDLL = entryName.toLowerCase().endsWith(".dll");
            boolean isLibsXZS = entryName.endsWith("libs.xzs");
            boolean isLibsZSTD = entryName.endsWith("libs.zstd");

            if (isSO || isDylib || isDLL || isLibsXZS || isLibsZSTD) {
                File libTmpFile = ArtifactScanner.extractZipEntryAsFile("native-lib", file, entry, entryName);
                String libPath = libTmpFile.getCanonicalPath();
                // Handle some special formats.
                if (isLibsXZS)
                    libPath = NativeScanner.getXZSLib(libPath);
                else if (isLibsZSTD)
                    libPath = NativeScanner.getZSTDLib(libPath);
                BinaryAnalysis analysis = NativeScanner.create(dbc, useRadare, libPath, preciseNativeStrings, truncateAddresses, demangle);
                // Check that the current mode supports .dll inputs.
                if (isDLL && !useRadare)
                    System.err.println("WARNING: Radare mode should be activated to scan library " + entryName);

                scanner.scanBinaryCode(analysis);
            }
        };
        for (String input : inputs) {
            System.out.println("Processing native code in input: " + input);
            try {
                (new ArtifactScanner()).processArchive(input, null, gProc);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }
}

// Intermediate data structure, communicates values between stages.
class SootData {
    public Set<SootClass> classes;
    public SootDriver driver;
    public FactWriter writer;
}
