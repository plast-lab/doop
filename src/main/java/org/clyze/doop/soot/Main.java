package org.clyze.doop.soot;

import org.clyze.doop.common.ArtifactEntry;
import org.clyze.doop.common.Database;
import org.clyze.doop.common.DoopErrorCodeException;
import org.clyze.doop.soot.android.AndroidSupport_Soot;
import org.clyze.utils.AARUtils;
import org.clyze.utils.Helper;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.options.Options;

import java.io.File;
import java.util.*;

public class Main {

    private static boolean isApplicationClass(SootParameters sootParameters, SootClass klass) {
        return sootParameters.isApplicationClass(klass.getName());
    }

    public static void main(String[] args) throws Throwable {
        SootParameters sootParameters = new SootParameters();
        try {
            if (args.length == 0) {
                System.err.println("usage: [options] file...");
                throw new DoopErrorCodeException(0);
            }

            for (int i = 0; i < args.length; i++) {
                int next_i = sootParameters.processNextArg(args, i);
                if (next_i != -1) {
                    i = next_i;
                    continue;
                }
                switch (args[i]) {
                    case "-h":
                    case "--help":
                    case "-help":
                        System.err.println("\nusage: [options] file");
                        System.err.println("options:");
                        System.err.println("  --main <class>                        Specify the name of the main class");
                        System.err.println("  --ssa                                 Generate SSA facts, enabling flow-sensitive analysis");
                        System.err.println("  --full                                Generate facts by full transitive resolution");
                        System.err.println("  -d <directory>                        Specify where to generate csv fact files");
                        System.err.println("  -l <archive>                          Find (library) classes in jar/zip archive");
                        System.err.println("  -ld <archive>                         Find (dependency) classes in jar/zip archive");
                        System.err.println("  -lsystem                              Find classes in default system classes");
                        System.err.println("  --deps <directory>                    Add jars in this directory to the class lookup path");
                        System.err.println("  --facts-subset                        Produce facts only for a subset of the given classes");
                        System.err.println("  --library-only-facts                  Generate facts only for library classes");
                        System.err.println("  --noFacts                             Don't generate facts (just empty files -- used for debugging)");
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

            // TODO
//            if (sootParameters._factsSubSet == SootParameters.FactsSubSet.APP_N_DEPS)
//                sootParameters.setDependencies(dependencies);
//            else
//                sootParameters.getLibraries().addAll(dependencies);

            sootParameters.finishArgProcessing();
            produceFacts(sootParameters);
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

    private static void produceFacts(SootParameters sootParameters) throws Exception {
        Options.v().set_output_dir(sootParameters.getOutputDir());
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

        BasicJavaSupport_Soot java = new BasicJavaSupport_Soot();
        java.preprocessInputs(sootParameters);

        AndroidSupport_Soot android = null;

        // Set of temporary directories to be cleaned up after analysis ends.
        Set<String> tmpDirs = new HashSet<>();
        if (sootParameters._android) {
            if (sootParameters.getInputs().size() > 1)
                System.err.println("\nWARNING -- Android mode: all inputs will be preprocessed but only " + sootParameters.getInputs().get(0) + " will be considered as application file. The rest of the input files may be ignored by Soot.\n");
            Options.v().set_process_multiple_dex(true);
            Options.v().set_src_prec(Options.src_prec_apk);
            android = new AndroidSupport_Soot(sootParameters, java);
            android.processInputs(sootParameters._androidJars, tmpDirs);
        } else {
            Options.v().set_src_prec(Options.src_prec_class);
        }

        Scene scene = Scene.v();
        List<String> inputs = sootParameters.getInputs();
        for (String input : inputs) {
            String inputFormat = input.endsWith(".jar")? "archive" : "file";
            System.out.println("Adding " + inputFormat + ": "  + input);

            addToSootClassPath(scene, input);
            if (sootParameters._android) {
                if (inputs.size() > 1)
                    System.out.println("WARNING: skipping rest of inputs");
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

        scene.loadNecessaryClasses();

        /*
         * This part should definitely appear after the call to
         * `Scene.loadNecessaryClasses()', since the latter may alter
         * the set of application classes by explicitly specifying
         * that some classes are library code (ignoring any previous
         * call to `setApplicationClass()').
         */

        classes.stream().filter((klass) -> isApplicationClass(sootParameters, klass)).forEachOrdered(SootClass::setApplicationClass);

        if (sootParameters._mode == SootParameters.Mode.FULL && sootParameters._factsSubSet == null)
            classes = new HashSet<>(scene.getClasses());

        try {
            System.out.println("Total classes in Scene: " + classes.size());
            DoopAddons.retrieveAllSceneClassesBodies();
            // The call below has a problem (only retrieves app method bodies).
            // DoopAddons.retrieveAllBodies();
            System.out.println("Retrieved all bodies");
        }
        catch (Exception ex) {
            System.out.println("Not all bodies retrieved");
        }

        try (Database db = new Database(new File(sootParameters.getOutputDir()))) {
            FactWriter writer = new FactWriter(db);
            ThreadFactory factory = new ThreadFactory(writer, sootParameters._ssa);
            Driver driver = new Driver(factory, classes.size(), sootParameters._cores);

            writer.writePreliminaryFacts(classes, java, sootParameters);
            db.flush();

            if (sootParameters._android) {
                if (sootParameters.getRunFlowdroid()) {
                    SootMethod dummyMain = android.getDummyMain();
                    if (dummyMain == null)
                        throw new RuntimeException("Internal error: FlowDroid returned null dummy main()");
                    driver.doAndroidInSequentialOrder(dummyMain, classes, writer, sootParameters._ssa);
                    return;
                } else {
                    Objects.requireNonNull(android).writeComponents(writer, sootParameters);
                }
            }

            if (!sootParameters.noFacts()) {
                scene.getOrMakeFastHierarchy();
                // avoids a concurrent modification exception, since we may
                // later be asking soot to add phantom classes to the scene's hierarchy
                driver.doInParallel(classes);
                if (sootParameters._generateJimple) {
                    Set<SootClass> jimpleClasses = new HashSet<>(classes);
                    if (sootParameters._factsSubSet == null) {
                        List<String> allClassNames = new ArrayList<>();
                        Map<String, Set<ArtifactEntry>> artifactToClassMap = java.getArtifactToClassMap();
                        for (String artifact : artifactToClassMap.keySet()) {
                            //                    if (!artifact.equals("rt.jar") && !artifact.equals("jce.jar") && !artifact.equals("jsse.jar") && !artifact.equals("android.jar"))
                            Set<String> artEntries = ArtifactEntry.toClassNames(artifactToClassMap.get(artifact));
                            allClassNames.addAll(artEntries);
                        }
                        forceResolveClasses(allClassNames, jimpleClasses, scene);
                        System.out.println("Total classes (application, dependencies and SDK) to generate Jimple for: " + jimpleClasses.size());
                    }
                    driver.writeInParallel(jimpleClasses);
                    DoopAddons.structureJimpleFiles(sootParameters.getOutputDir());
                }
            }

            writer.writeLastFacts(java);
        } finally {
            // Clean up any temporary directories used for AAR extraction.
            Helper.cleanUp(tmpDirs);
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

    private static void forceResolveClasses(Collection<String> classesToResolve, Collection<SootClass> resolvedClasses, Scene scene) {
        for (String className : classesToResolve) {
            scene.forceResolve(className, SootClass.BODIES);
            SootClass c = scene.loadClass(className, SootClass.BODIES);
            resolvedClasses.add(c);
        }
    }

}
