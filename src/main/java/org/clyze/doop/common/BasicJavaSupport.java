package org.clyze.doop.common;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import org.objectweb.asm.ClassReader;

/**
 * This class gathers Java-specific code (such as JAR handling).
 */
public class BasicJavaSupport {

    protected final Set<String> classesInApplicationJars = new HashSet<>();
    protected final Set<String> classesInLibraryJars = new HashSet<>();
    protected final Set<String> classesInDependencyJars = new HashSet<>();
    private final Map<String, Set<ArtifactEntry>> artifactToClassMap = new ConcurrentHashMap<>();
    private final PropertyProvider propertyProvider = new PropertyProvider();
    private final Parameters parameters;

    public BasicJavaSupport(Parameters parameters) {
        this.parameters = parameters;
    }

    /**
     * Helper method to read classes and resources from input archives.
     *
     * @param parameters the list of all the given parameters
     *
     */
    public void preprocessInputs() throws IOException {
        for (String filename : parameters.getInputs()) {
            System.out.println("Preprocessing application: " + filename);
            preprocessInput(classesInApplicationJars, filename);
        }
        for (String filename : parameters.getPlatformLibs()) {
            System.out.println("Preprocessing platform library: " + filename);
            preprocessInput(classesInLibraryJars, filename);
        }
        for (String filename : parameters.getDependencies()) {
            System.out.println("Preprocessing dependency: " + filename);
            preprocessInput(classesInDependencyJars, filename);
        }
    }

    /**
     * Preprocess an input archive.
     *
     * @param classSet   appropriate set to add class names
     * @param filename   the input filename
     */
    private void preprocessInput(Collection<String> classSet, String filename) throws IOException {
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
                    } catch (Exception e) {
                        System.err.println("Error while preprocessing entry \"" + entryName + "\", it will be ignored.");
                    }
                } else if (entryName.endsWith(".properties")) {
                    propertyProvider.addProperties(jarFile.getInputStream(entry), filename);
                } else if (parameters._scanNativeCode && entryName.endsWith(".so")) {
                    File tmpDir = Files.createTempDirectory("native-lib").toFile();
                    tmpDir.deleteOnExit();
                    String tmpName = entryName.replaceAll(File.separator, "_");
                    File libTmpFile = new File(tmpDir, tmpName);
                    libTmpFile.deleteOnExit();
                    Files.copy(jarFile.getInputStream(entry), libTmpFile.toPath());
                    File outDir = new File(parameters.getOutputDir());
                    NativeScanner.scan("nm", "objdump", libTmpFile, outDir);
                }
            }
        }
    }

    public PropertyProvider getPropertyProvider() {
        return propertyProvider;
    }

    public Map<String, Set<ArtifactEntry>> getArtifactToClassMap() {
        return artifactToClassMap;
    }

    /**
     * Registers a class with its container artifact.
     * @param artifact     the file name of the artifact containing the class
     * @param className    the name of the class
     * @param subArtifact  the sub-artifact (such as "classes.dex" for APKs)
     */
    public void registerArtifactClass(String artifact, String className, String subArtifact) {
        ArtifactEntry ae = new ArtifactEntry(className, subArtifact);
        artifactToClassMap.computeIfAbsent(artifact, x -> new CopyOnWriteArraySet<>()).add(ae);
    }

    public Set<String> getClassesInApplicationJars() {
        return classesInApplicationJars;
    }

    public Set<String> getClassesInLibraryJars() {
        return classesInLibraryJars;
    }

    public Set<String> getClassesInDependencyJars() {
        return classesInDependencyJars;
    }
}
