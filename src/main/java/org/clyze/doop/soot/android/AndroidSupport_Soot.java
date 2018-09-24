package org.clyze.doop.soot.android;

import java.io.File;
import java.io.IOException;
import java.util.*;
import org.clyze.doop.common.android.AppResources;
import org.clyze.doop.common.android.AppResourcesXML;
import org.clyze.doop.common.android.AndroidSupport;
import org.clyze.doop.soot.*;
import org.jf.dexlib2.dexbacked.DexBackedClassDef;
import soot.Scene;
import soot.SootClass;
import soot.coffi.Util;
import soot.dexpler.DexFileProvider;

import static soot.dexpler.DexFileProvider.DexContainer;

public class AndroidSupport_Soot extends AndroidSupport implements ClassAdder {

    public AndroidSupport_Soot(SootParameters sootParameters, BasicJavaSupport_Soot java) {
        super(sootParameters, java);
    }

    /**
     * Find all classes inside an APK archive and add them to a Soot scene.
     *
     * @param classes   a set of Soot classes to receive the new classes
     * @param scene     the Soot Scene object
     * @param inputApk  the filename of the APK
     */
    private void addClasses(Collection<SootClass> classes, Scene scene, String inputApk) {
        File apk = new File(inputApk);
        System.out.println("Android mode, APK = " + inputApk);
        String artifact = apk.getName();
        try {
            List<DexContainer> listContainers = DexFileProvider.v().getDexFromSource(apk);
            Set<Object> allDexClasses = new HashSet<>();
            for (DexContainer dexContainer : listContainers) {
                Set<? extends DexBackedClassDef> dexClasses = dexContainer.getBase().getClasses();
                allDexClasses.addAll(dexClasses);
                for (DexBackedClassDef dexBackedClassDef : dexClasses) {
                    String escapeClassName = Util.v().jimpleTypeOfFieldDescriptor((dexBackedClassDef).getType()).toQuotedString();
                    SootClass c = scene.loadClass(escapeClassName, SootClass.BODIES);
                    classes.add(c);
                    java.registerArtifactClass(artifact, escapeClassName, dexContainer.getDexName());
                }
            }
            System.out.println("Classes found in apk: " + allDexClasses.size());
        } catch (IOException ex) {
            System.err.println("Could not read dex classes in " + apk);
            ex.printStackTrace();
        }
    }

    private void addClasses(Iterable<String> inputs, Collection<SootClass> classes, Scene scene, Iterable<String> target) {
        for (String input : inputs) {
            if (input.endsWith(".apk")) {
                addClasses(classes, scene, input);
            } else {
                // We support both AAR and JAR inputs, although JARs
                // are not ideal for analysis in Android, as they
                // don't contain AppResources.xml.
                System.out.println("Android mode, input = " + input);
                ((BasicJavaSupport_Soot)java).addSootClasses(target, classes, scene);
            }
        }
    }

    @Override
    public void addAppClasses(Set<SootClass> classes, Scene scene) {
        addClasses(parameters.getInputs(), classes, scene, java.getClassesInApplicationJars());
    }

    @Override
    public void addLibClasses(Set<SootClass> classes, Scene scene) {
        addClasses(parameters.getPlatformLibs(), classes, scene, java.getClassesInLibraryJars());
    }

    @Override
    public void addDepClasses(Set<SootClass> classes, Scene scene) {
        addClasses(parameters.getDependencies(), classes, scene, java.getClassesInDependencyJars());
    }

    // Parses Android manifests. Supports binary and plain-text XML
    // files (found in .apk and .aar files respectively).
    @Override
    public AppResources processAppResources(String archiveLocation) throws Exception {
        String path = archiveLocation.toLowerCase();
        if (path.endsWith(".apk"))
            return new AppResourcesAXML(archiveLocation);
        else if (path.endsWith(".aar"))
            return AppResourcesXML.fromAAR(archiveLocation);
        else
            throw new RuntimeException("Unknown archive format: " + archiveLocation);
    }

}
