package org.clyze.doop.soot;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import org.clyze.doop.common.ArtifactEntry;
import org.clyze.doop.common.BasicJavaSupport;
import org.clyze.doop.common.PropertyProvider;
import soot.Scene;
import soot.SootClass;
import soot.SourceLocator;

public class BasicJavaSupport_Soot extends BasicJavaSupport {

    public BasicJavaSupport_Soot(Map<String, Set<ArtifactEntry>> artifactToClassMap, PropertyProvider propertyProvider) {
        super(artifactToClassMap, propertyProvider);
    }

    /**
     * Helper method to read classes and property files from JAR/AAR files.
     *
     * @param sootParameters the list of all the given soot parameters
     *
     * @return the name of the JAR file that was processed; this is
     * either the original first parameter, or the locally saved
     * classes.jar found in the .aar file (if such a file was given)
     *
     */
    public void populateClassesInAppJar(SootParameters sootParameters) throws IOException {
        for (String filename : sootParameters.getInputs()) {
            System.out.println("Processing application JAR: " + filename);
            processJar(classesInApplicationJars, filename);
        }
        for (String filename :  sootParameters.getLibraries()) {
            System.out.println("Processing library JAR: " + filename);
            processJar(classesInLibraryJars, filename);
        }
        for (String filename :  sootParameters._dependencies) {
            System.out.println("Processing dependency JAR: " + filename);
            processJar(classesInDependencyJars, filename);
        }
    }

    protected void addSootClasses(Collection<String> classesToLoad, Collection<SootClass> loadedClasses, Scene scene) {
        for (String className : classesToLoad) {
            SootClass c = scene.loadClass(className, SootClass.BODIES);
            loadedClasses.add(c);
        }
    }

    public void addAppClasses(Set<SootClass> classes, Scene scene) {
        addSootClasses(classesInApplicationJars, classes, scene);
        addBasicClasses(scene);
        System.out.println("Classes in input (application) jar(s): " + classesInApplicationJars.size());
    }

    public void addLibClasses(Set<SootClass> classes, Scene scene) {
        addSootClasses(classesInLibraryJars, classes, scene);
        addBasicClasses(scene);
        System.out.println("Classes in library jar(s): " + classesInLibraryJars.size());
    }

    public void addDepClasses(Set<SootClass> classes, Scene scene) {
        addSootClasses(classesInDependencyJars, classes, scene);
        System.out.println("Classes in dependency jar(s): " + classesInDependencyJars.size());
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

    private static void addCommonDynamicClass(Scene scene, String className) {
        if (SourceLocator.v().getClassSource(className) != null) {
            scene.addBasicClass(className);
        }
    }
}
