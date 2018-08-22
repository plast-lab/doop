package org.clyze.doop.soot.android;

import java.io.File;
import java.io.IOException;
import java.util.*;
import org.clyze.doop.common.android.AndroidManifest;
import org.clyze.doop.common.android.AndroidManifestXML;
import org.clyze.doop.common.android.AndroidSupport;
import org.clyze.doop.soot.*;
import org.clyze.doop.soot.android.AndroidManifestAXML;
import org.jf.dexlib2.dexbacked.DexBackedClassDef;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.coffi.Util;
import soot.dexpler.DexFileProvider;
import soot.jimple.infoflow.android.SetupApplication;

import static soot.dexpler.DexFileProvider.DexContainer;
import static soot.jimple.infoflow.android.InfoflowAndroidConfiguration.CallbackAnalyzer.Fast;

public class AndroidSupport_Soot extends AndroidSupport implements ClassAdder {

    private SootMethod dummyMain;

    public AndroidSupport_Soot(SootParameters sootParameters, BasicJavaSupport_Soot java) {
        super(sootParameters, java);
    }

    public SootMethod getDummyMain() {
        return dummyMain;
    }

    protected SootParameters getSootParameters() {
        if (parameters instanceof SootParameters)
            return (SootParameters)parameters;
        else
            throw new RuntimeException("Internal error: field type != SootParameters");
    }

    public void processInputs(String androidJars, Set<String> tmpDirs) throws Exception {
        SootParameters sootParameters = getSootParameters();
        if (sootParameters.getRunFlowdroid()) {
            String appInput = parameters.getInputs().get(0);
            SetupApplication app = new SetupApplication(androidJars, appInput);
            // TODO: fix this method call (refactored in newer
            // versions of FlowDroid):
            //app.getConfig().setCallbackAnalyzer(Fast);
            String filename = Objects.requireNonNull(Main.class.getClassLoader().getResource("SourcesAndSinks.txt")).getFile();
            try {
                // TODO: fix this method call (refactored in newer
                // versions of FlowDroid):
                // app.calculateSourcesSinksEntryPoints(filename);
                throw new RuntimeException("FlowDroid interface missing");
            } catch (Exception ex) {
                System.err.println("calculateSourcesSinksEntrypoints() failed:");
                ex.printStackTrace();
            }
            dummyMain = app.getDummyMainMethod();
            if (dummyMain == null) {
                throw new RuntimeException("Dummy main null");
            }
        } else
            super.processInputs(tmpDirs);
    }

    @Override
    public void addAppClasses(Set<SootClass> classes, Scene scene) {
        for (String appInput : parameters.getInputs()) {
            if (appInput.endsWith(".apk")) {
                File apk = new File(appInput);
                System.out.println("Android mode, APK = " + appInput);
                String artifact = apk.getName();
                try {
                    List<DexContainer> listContainers = DexFileProvider.v().getDexFromSource(apk);
                    Set<Object> allDexClasses = new HashSet<>();
                    for (DexContainer dexContainer : listContainers) {
                        Set<? extends DexBackedClassDef> dexClasses = dexContainer.getBase().getClasses();
                        allDexClasses.addAll(dexClasses);
                        for (DexBackedClassDef dexBackedClassDef : dexClasses) {
                            String escapeClassName = Util.v().jimpleTypeOfFieldDescriptor((dexBackedClassDef).getType()).getEscapedName();
                            SootClass c = scene.loadClass(escapeClassName, SootClass.BODIES);
                            classes.add(c);
                            java.registerArtifactClass(artifact, escapeClassName, dexContainer.getDexName());
                        }
                    }
                    System.out.println("Classes found in apk: " + allDexClasses.size());
                } catch (IOException ex) {
                    System.err.println("Could not read dex classes in " + apk);
                    ex.printStackTrace();
                    return;
                }
            } else {
                // We support both AAR and JAR inputs, although JARs
                // are not ideal for analysis in Android, as they
                // don't contain AndroidManifest.xml.
                System.out.println("Android mode, input = " + appInput);
                ((BasicJavaSupport_Soot)java).addSootClasses(java.getClassesInApplicationJars(), classes, scene);
            }
        }
    }

    @Override
    public void addLibClasses(Set<SootClass> classes, Scene scene) {
        throw new RuntimeException("Not implemented: addLibClasses()");
    }

    @Override
    public void addDepClasses(Set<SootClass> classes, Scene scene) {
        throw new RuntimeException("Not implemented: addDepClasses()");
    }

    // Parses Android manifests. Supports binary and plain-text XML
    // files (found in .apk and .aar files respectively).
    public static AndroidManifest newAndroidManifest(String archiveLocation) throws Exception {
        try {
            return new AndroidManifestAXML(archiveLocation);
        } catch (Exception ex) {
            return AndroidManifestXML.fromArchive(archiveLocation);
        }
    }

    @Override
    public AndroidManifest getAndroidManifest(String archiveLocation) throws Exception {
        return newAndroidManifest(archiveLocation);
    }

}
