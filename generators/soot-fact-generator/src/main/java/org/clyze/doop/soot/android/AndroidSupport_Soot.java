package org.clyze.doop.soot.android;

import java.util.*;
import org.clyze.doop.common.android.AppResources;
import org.clyze.doop.common.android.AppResourcesXML;
import org.clyze.doop.common.android.AndroidSupport;
import org.clyze.doop.soot.*;
import soot.Scene;
import soot.SootClass;

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
        System.out.println("Android mode, APK = " + inputApk);
        java.getArtifactScanner().processAPKClasses(inputApk, (className -> classes.add(scene.loadClass(className, SootClass.BODIES))));
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

    @Override
    public boolean isAppClass(String t) {
        return java.getClassesInApplicationJars().contains(t);
    }

    @Override
    public boolean isLibClass(String t) {
        return java.getClassesInLibraryJars().contains(t);
    }

    @Override
    public boolean isAppOrDepClass(String t) {
        return java.getClassesInDependencyJars().contains(t) || isAppClass(t);
    }

    // Parses Android manifests. Supports binary and plain-text XML
    // files (found in .apk and .aar files respectively).
    @Override
    public AppResources processAppResources(String archiveLocation) throws Exception {
        String path = archiveLocation.toLowerCase();
        if (path.endsWith(".apk"))
            return AppResourcesXML.fromAPK(archiveLocation);
        else if (path.endsWith(".aar"))
            return AppResourcesXML.fromAAR(archiveLocation);
        else
            throw new RuntimeException("Unknown archive format: " + archiveLocation);
    }

}
