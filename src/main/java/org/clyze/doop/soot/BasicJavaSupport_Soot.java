package org.clyze.doop.soot;

import java.util.Collection;
import java.util.Set;
import org.clyze.doop.common.BasicJavaSupport;
import soot.Scene;
import soot.SootClass;
import soot.SourceLocator;

public class BasicJavaSupport_Soot extends BasicJavaSupport implements ClassAdder {

    public void addSootClasses(Collection<String> classesToLoad, Collection<SootClass> loadedClasses, Scene scene) {
        for (String className : classesToLoad) {
            SootClass c = scene.loadClass(className, SootClass.BODIES);
            loadedClasses.add(c);
        }
    }

    @Override
    public void addAppClasses(Set<SootClass> classes, Scene scene) {
        addSootClasses(classesInApplicationJars, classes, scene);
        addBasicClasses(scene);
        System.out.println("Classes in input (application) jar(s): " + classesInApplicationJars.size());
    }

    @Override
    public void addLibClasses(Set<SootClass> classes, Scene scene) {
        addSootClasses(classesInLibraryJars, classes, scene);
        addBasicClasses(scene);
        System.out.println("Classes in library jar(s): " + classesInLibraryJars.size());
    }

    @Override
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
