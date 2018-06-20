package org.clyze.doop.soot;

import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import org.objectweb.asm.ClassReader;
import soot.Scene;
import soot.SootClass;
import soot.SourceLocator;
import soot.SourceLocator.FoundFile;

/**
 * This class gathers Java-specific code (such as JAR handling).
 */
public class BasicJavaSupport {

    protected Set<String> classesInApplicationJars;
    private Map<String, Set<ArtifactEntry>> artifactToClassMap;
    private PropertyProvider propertyProvider;

    public BasicJavaSupport(Set<String> classesInApplicationJars, Map<String, Set<ArtifactEntry>> artifactToClassMap, PropertyProvider propertyProvider) {
        this.classesInApplicationJars = classesInApplicationJars;
        this.artifactToClassMap = artifactToClassMap;
        this.propertyProvider = propertyProvider;
    }

    /**
     * Helper method to read classes and property files from JAR/AAR files.
     *
     * @param inputFilenames           the list of all input jar file names
     * @param libraryFilenames         the list of all library file names
     * @param classesInApplicationJars the set to populate
     * @param artifactToClassMap       map from artifacts to class entries
     * @param propertyProvider         the provider to use for .properties files
     *
     * @return the name of the JAR file that was processed; this is
     * either the original first parameter, or the locally saved
     * classes.jar found in the .aar file (if such a file was given)
     *
     */
    public void populateClassesInAppJar(List<String> inputFilenames,
                                        List<String> libraryFilenames) throws IOException {
        for (String inputFilename : inputFilenames)
            processJar("application", inputFilename);

        for (String libraryFilename : libraryFilenames) {
            processJar("library", libraryFilename);
        }
    }

    private void processJar(String desc, String filename) throws IOException {
        JarEntry entry;
        try (JarInputStream jin = new JarInputStream(new FileInputStream(filename));
             JarFile jarFile = new JarFile(filename)) {
            System.out.println("Processing " + desc + " JAR: " + filename);
            /* List all JAR entries */
            while ((entry = jin.getNextJarEntry()) != null) {
                /* Skip directories */
                if (entry.isDirectory())
                    continue;

                String entryName = entry.getName();
                if (entryName.endsWith(".class")) {
                    ClassReader reader = new ClassReader(jarFile.getInputStream(entry));
                    String className = reader.getClassName().replace("/", ".");
                    classesInApplicationJars.add(className);
                    String artifact = filename.substring(filename.lastIndexOf('/') + 1, filename.length());
                    registerArtifactClass(artifact, className, "-");
                } else if (entryName.endsWith(".properties")) {
                    propertyProvider.addProperties((new FoundFile(filename, entryName)));
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

    public void addClasses(Set<SootClass> classes, Scene scene) {
        addSootClasses(classesInApplicationJars, classes, scene);
        addBasicClasses(scene);

        System.out.println("Classes in input (application) jar(s): " + classesInApplicationJars.size());
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
