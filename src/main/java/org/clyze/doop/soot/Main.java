package org.clyze.doop.soot;

import java.io.File;
import java.io.IOException;
import java.util.*;
import org.clyze.doop.common.ArtifactEntry;
import org.clyze.doop.common.Database;
import org.clyze.doop.common.DoopErrorCodeException;
import org.clyze.doop.soot.android.AndroidSupport_Soot;
import org.clyze.utils.AARUtils;
import org.clyze.utils.JHelper;
import org.xmlpull.v1.XmlPullParserException;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.jimple.infoflow.InfoflowConfiguration.ImplicitFlowMode;
import soot.jimple.infoflow.android.InfoflowAndroidConfiguration;
import soot.jimple.infoflow.android.SetupApplication;
import soot.jimple.infoflow.android.InfoflowAndroidConfiguration.LayoutMatchingMode;
import soot.jimple.infoflow.taintWrappers.EasyTaintWrapper;
import soot.options.Options;

import static soot.jimple.infoflow.android.InfoflowAndroidConfiguration.CallbackAnalyzer.Fast;

public class Main {

    private static boolean isApplicationClass(SootParameters sootParameters, SootClass klass) {
        return sootParameters.isApplicationClass(klass.getName());
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("usage: [options] file...");
            throw new DoopErrorCodeException(0);
        }

        SootParameters sootParameters = new SootParameters();
        try {
            sootParameters.initFromArgs(args);
            produceFacts(sootParameters);
        } catch (Exception ex) {
            ex.printStackTrace();
            throw ex;
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
        } else
            Options.v().set_output_format(Options.output_format_jimple);

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
            android.processInputs(tmpDirs);
        } else
            Options.v().set_src_prec(Options.src_prec_class);

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
            DoopAddons.retrieveAllSceneClassesBodies(sootParameters._cores);
            // The call below has a problem (only retrieves app method bodies).
            // DoopAddons.retrieveAllBodies();
            System.out.println("Retrieved all bodies.");
        }
        catch (Exception ex) {
            System.err.println("Error: not all bodies retrieved.");
        }

        try (Database db = new Database(new File(sootParameters.getOutputDir()))) {
            boolean reportPhantoms = sootParameters._reportPhantoms;
            FactWriter writer = new FactWriter(db, reportPhantoms);
            ThreadFactory factory = new ThreadFactory(writer, sootParameters._ssa, reportPhantoms);
            SootDriver driver = new SootDriver(factory, classes.size(), sootParameters._cores, sootParameters._ignoreFactGenErrors);
            factory.setDriver(driver);

            writer.writePreliminaryFacts(classes, java, sootParameters);
            db.flush();

            if (android != null)
                android.writeComponents(writer, sootParameters);

            if (!sootParameters.noFacts()) {
                scene.getOrMakeFastHierarchy();

                if (sootParameters._android && sootParameters.getRunFlowdroid()) {
                    SootMethod dummyMain = getDummyMain(sootParameters.getInputs().get(0), sootParameters._androidJars);
                    if (dummyMain == null)
                        throw new RuntimeException("Internal error: could not compute dummy main() with FlowDroid");
                    System.out.println("Generated dummy main method " + dummyMain.getName() + "()");
                    driver.generateMethod(dummyMain, writer, sootParameters._ssa, reportPhantoms);
                }

                // avoids a concurrent modification exception, since we may
                // later be asking soot to add phantom classes to the scene's hierarchy
                driver.generateInParallel(classes);
                if (sootParameters._generateJimple) {
                    Set<SootClass> jimpleClasses = new HashSet<>(classes);
                    if (sootParameters._factsSubSet == null) {
                        Collection<String> allClassNames = new ArrayList<>();
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
            JHelper.cleanUp(tmpDirs);
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

    private static void forceResolveClasses(Iterable<String> classesToResolve, Collection<SootClass> resolvedClasses, Scene scene) {
        for (String className : classesToResolve) {
            scene.forceResolve(className, SootClass.BODIES);
            SootClass c = scene.loadClass(className, SootClass.BODIES);
            resolvedClasses.add(c);
        }
    }

    /**
     * Call FlowDroid to calculate a dummy main method.
     */
    private static SootMethod getDummyMain(String appInput, String androidJars) {
        if (!DoopAddons.usingUpstream())
            System.err.println("WARNING: FlowDroid is only supported when using upstream Soot (see build.gradle).");

        Options.v().set_wrong_staticness(Options.wrong_staticness_ignore);

        SetupApplication app = new SetupApplication(androidJars, appInput);
        InfoflowAndroidConfiguration config = app.getConfig();
        config.setMergeDexFiles(true);
        config.getCallbackConfig().setCallbackAnalyzer(Fast);
        // config.setImplicitFlowMode(ImplicitFlowMode.AllImplicitFlows);
        config.setImplicitFlowMode(ImplicitFlowMode.NoImplicitFlows);
        config.getSourceSinkConfig().setLayoutMatchingMode(LayoutMatchingMode.MatchAll);

        String sourcesAndSinks = Objects.requireNonNull(Main.class.getClassLoader().getResource("SourcesAndSinks.txt")).getFile();
        String taintWrapper = Objects.requireNonNull(Main.class.getClassLoader().getResource("EasyTaintWrapperSource.txt")).getFile();
        try {
            app.setTaintWrapper(new EasyTaintWrapper(new File(taintWrapper)));
            app.runInfoflow(sourcesAndSinks);
            return app.getDummyMainMethod();
        } catch (IOException | XmlPullParserException ex) {
            System.err.println("FlowDroid failed:");
            ex.printStackTrace();
        }
        return null;
    }

}
