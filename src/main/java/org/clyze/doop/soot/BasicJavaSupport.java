package org.clyze.doop.soot;

import org.objectweb.asm.ClassReader;
import soot.Scene;
import soot.SootClass;
import soot.SourceLocator;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;

/**
 * This class gathers Java-specific code (such as JAR handling).
 */
public class BasicJavaSupport {

    protected Set<String> classesInApplicationJars;
    protected Set<String> classesInLibraryJars;
    protected Set<String> classesInDependencyJars;
    private Map<String, Set<ArtifactEntry>> artifactToClassMap;
    private PropertyProvider propertyProvider;

    public BasicJavaSupport(Map<String, Set<ArtifactEntry>> artifactToClassMap, PropertyProvider propertyProvider) {
        this.classesInApplicationJars = new HashSet<>();
        this.classesInLibraryJars = new HashSet<>();
        this.classesInDependencyJars = new HashSet<>();
        this.artifactToClassMap = artifactToClassMap;
        this.propertyProvider = propertyProvider;
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
        for (String filename : sootParameters._inputs) {
            System.out.println("Processing application JAR: " + filename);
            processJar(classesInApplicationJars, filename);
        }
        for (String filename :  sootParameters._libraries) {
            System.out.println("Processing library JAR: " + filename);
            processJar(classesInLibraryJars, filename);
        }
        for (String filename :  sootParameters._dependencies) {
            System.out.println("Processing dependency JAR: " + filename);
            processJar(classesInDependencyJars, filename);
        }
    }

    /**
     * Process a JAR input.
     * @param classSet   appropriate set to add class names
     * @param filename   the JAR filename
     */
    private void processJar(Set<String> classSet, String filename) throws IOException {
        JarEntry entry;
        try (JarInputStream jin = new JarInputStream(new FileInputStream(filename));
             JarFile jarFile = new JarFile(filename)) {
            /* List all JAR entries */
            while ((entry = jin.getNextJarEntry()) != null) {
                /* Skip directories */
                if (entry.isDirectory())
                    continue;

                String entryName = entry.getName();
                if (entryName.endsWith(".class")) {
                    try {
                        ClassReader reader = new ClassReader(jarFile.getInputStream(entry));
                        String className = reader.getClassName().replace("/", ".");
                        classSet.add(className);
                        String artifact = (new File(jarFile.getName())).getName();
                        registerArtifactClass(artifact, className, "-");
                    } catch (IllegalArgumentException e) {
                        System.err.println("-- Problematic .class file \"" + entryName + "\"");
                    }
                } else if (entryName.endsWith(".properties")) {
                    propertyProvider.addProperties((new SourceLocator.FoundFile(filename, entryName)));
                } /* Skip non-class files and non-property files */
            }
        }
    }

    /**
     * Registers a class with its container artifact.
     * @param artifact     the file name of the artifact containing the class
     * @param className    the name of the class
     * @param subArtifact  the sub-artifact (such as "classes.dex" for APKs)
     */
    protected void registerArtifactClass(String artifact, String className, String subArtifact) {
        ArtifactEntry ae = new ArtifactEntry(className, subArtifact);
        if (!artifactToClassMap.containsKey(artifact)) {
            Set<ArtifactEntry> artifactClasses = new HashSet<>();
            artifactClasses.add(ae);
            artifactToClassMap.put(artifact, artifactClasses);
        } else
            artifactToClassMap.get(artifact).add(ae);
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
