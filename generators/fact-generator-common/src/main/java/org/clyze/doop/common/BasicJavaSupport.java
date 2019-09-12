package org.clyze.doop.common;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * This class gathers Java-specific code (such as JAR handling).
 */
public class BasicJavaSupport {

    protected final Set<String> classesInApplicationJars = new HashSet<>();
    protected final Set<String> classesInLibraryJars = new HashSet<>();
    protected final Set<String> classesInDependencyJars = new HashSet<>();
    private final PropertyProvider propertyProvider = new PropertyProvider();
    private final Parameters parameters;
    private final ArtifactScanner artScanner;

    public BasicJavaSupport(Parameters parameters, ArtifactScanner artScanner) {
        this.parameters = parameters;
        this.artScanner = artScanner;
    }

    public ArtifactScanner getArtifactScanner() {
        return artScanner;
    }

    /**
     * Helper method to read classes and resources from input archives.
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
        boolean isAar = filename.toLowerCase().endsWith(".aar");
        boolean isJar = filename.toLowerCase().endsWith(".jar");

        ArtifactScanner.EntryProcessor gProc = (jarFile, entry, entryName) -> {
            File outDir = new File(parameters.getOutputDir());
            if (entryName.endsWith(".properties"))
                propertyProvider.addProperties(jarFile.getInputStream(entry), filename);
            else if ((isJar || isAar) && entryName.endsWith(".xml")) {
                // We only handle .xml entries inside JAR archives here.
                // APK archives may contain binary XML and need decoding.
                File xmlTmpFile = extractZipEntryAsFile("xml-file", jarFile, entry, entryName);
                try (Database db = new Database(outDir)) {
                    System.out.println("Processing XML entry (in " + filename + "): " + entryName);
                    XMLFactGenerator.processFile(xmlTmpFile, db, "");
                }
            } else if (parameters._scanNativeCode) {
                boolean isSO = entryName.endsWith(".so");
                boolean isLibsXZS = entryName.endsWith("libs.xzs");
                boolean isLibsZSTD = entryName.endsWith("libs.zstd");
                if (isSO || isLibsXZS || isLibsZSTD) {
                    File libTmpFile = extractZipEntryAsFile("native-lib", jarFile, entry, entryName);
                    if (isSO)
                        NativeScanner.scanLib(libTmpFile, outDir);
                    else if (isLibsXZS)
                        NativeScanner.scanXZSLib(libTmpFile, outDir);
                    else if (isLibsZSTD)
                        NativeScanner.scanZSTDLib(libTmpFile, outDir);
                }
            }
        };
        artScanner.processJARClasses(filename, classSet::add, gProc);
    }

    /**
     * Helper method to extract an entry inside a JAR archive and save
     * it as a file.
     *
     * @param tmpDirName   a name for the intermediate temporary directory
     * @param jarFile      the JAR archive
     * @param entry        the archive entry
     * @param entryName    the name of the entry
     * @return             the output file
     */
    private static File extractZipEntryAsFile(String tmpDirName, JarFile jarFile, JarEntry entry, String entryName) throws IOException {
        File tmpDir = Files.createTempDirectory(tmpDirName).toFile();
        tmpDir.deleteOnExit();
        String tmpName = entryName.replaceAll(File.separator, "_");
        File libTmpFile = new File(tmpDir, tmpName);
        libTmpFile.deleteOnExit();
        Files.copy(jarFile.getInputStream(entry), libTmpFile.toPath());
        return libTmpFile;
    }

    public PropertyProvider getPropertyProvider() {
        return propertyProvider;
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
