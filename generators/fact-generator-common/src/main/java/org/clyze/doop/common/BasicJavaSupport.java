package org.clyze.doop.common;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.clyze.doop.common.scanner.NativeScanner;

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
    // Executor for async big tasks such as apk decoding or library scanning.
    private final ExecutorService exec = Executors.newFixedThreadPool(3);

    public BasicJavaSupport(Parameters parameters, ArtifactScanner artScanner) {
        this.parameters = parameters;
        this.artScanner = artScanner;
    }

    public ArtifactScanner getArtifactScanner() {
        return artScanner;
    }

    public ExecutorService getExecutor() {
        return exec;
    }

    /**
     * Helper method to read classes and resources from input archives.
     */
    public void preprocessInputs(Database db) throws IOException {
        for (String filename : parameters.getInputs()) {
            System.out.println("Preprocessing application: " + filename);
            preprocessInput(db, classesInApplicationJars, filename);
        }
        for (String filename : parameters.getPlatformLibs()) {
            System.out.println("Preprocessing platform library: " + filename);
            preprocessInput(db, classesInLibraryJars, filename);
        }
        for (String filename : parameters.getDependencies()) {
            System.out.println("Preprocessing dependency: " + filename);
            preprocessInput(db, classesInDependencyJars, filename);
        }
    }

    /**
     * Preprocess an input archive.
     *
     * @param db         the database object to use
     * @param classSet   appropriate set to add class names
     * @param filename   the input filename
     */
    private void preprocessInput(Database db, Collection<String> classSet, String filename) throws IOException {
        boolean isAar = filename.toLowerCase().endsWith(".aar");
        boolean isJar = filename.toLowerCase().endsWith(".jar");
        boolean isClass = filename.toLowerCase().endsWith(".class");
        boolean isApk = filename.toLowerCase().endsWith(".apk");

        ArtifactScanner.EntryProcessor gProc = (jarFile, entry, entryName) -> {
            File outDir = new File(parameters.getOutputDir());
            if (entryName.endsWith(".properties"))
                propertyProvider.addProperties(jarFile.getInputStream(entry), filename);
            else if ((isJar || isAar) && entryName.endsWith(".xml")) {
                // We only handle .xml entries inside JAR archives here.
                // APK archives may contain binary XML and need decoding.
                File xmlTmpFile = extractZipEntryAsFile("xml-file", jarFile, entry, entryName);
                System.out.println("Processing XML entry (in " + filename + "): " + entryName);
                XMLFactGenerator.processFile(xmlTmpFile, db, "");
            } else if (parameters._scanNativeCode) {
                boolean isSO = entryName.endsWith(".so");
                boolean isLibsXZS = entryName.endsWith("libs.xzs");
                boolean isLibsZSTD = entryName.endsWith("libs.zstd");
                if (isSO || isLibsXZS || isLibsZSTD) {
                    File libTmpFile = extractZipEntryAsFile("native-lib", jarFile, entry, entryName);
                    NativeScanner scanner = new NativeScanner(parameters._radare, parameters._preciseNativeStrings);
                    if (isSO)
                        scanner.scanLib(libTmpFile, outDir, db);
                    else if (isLibsXZS)
                        scanner.scanXZSLib(libTmpFile, outDir, db);
                    else if (isLibsZSTD)
                        scanner.scanZSTDLib(libTmpFile, outDir, db);
                }
            }
        };
        if (isJar)
            artScanner.processJARClasses(filename, classSet::add, gProc);
        else if (isClass) {
            File f = new File(filename);
            try (FileInputStream fis = new FileInputStream(f)) {
                artScanner.processClass(fis, f, classSet::add);
            }
        }
        else if (!isApk)
            System.err.println("WARNING: artifact scanner skips " + filename);
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
