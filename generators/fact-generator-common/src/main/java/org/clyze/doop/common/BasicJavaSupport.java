package org.clyze.doop.common;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/**
 * This class gathers Java-specific code (such as JAR handling).
 */
public class BasicJavaSupport {

    protected final Set<String> classesInApplicationJars = ConcurrentHashMap.newKeySet();
    protected final Set<String> classesInLibraryJars = ConcurrentHashMap.newKeySet();
    protected final Set<String> classesInDependencyJars = ConcurrentHashMap.newKeySet();
    private final PropertyProvider propertyProvider = new PropertyProvider();
    private final Parameters parameters;
    private final ArtifactScanner artScanner;
    // Executor for async big tasks such as apk decoding or library scanning.
    private final ExecutorService exec = Executors.newFixedThreadPool(3);
    public final Collection<String> xmlRoots = ConcurrentHashMap.newKeySet();

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
    public void preprocessInputs(Database db, Set<String> tmpDirs) throws IOException {
        for (String filename : parameters.getInputs()) {
            System.out.println("Preprocessing application: " + filename);
            preprocessInput(db, tmpDirs, classesInApplicationJars, filename);
        }
        for (String filename : parameters.getPlatformLibs()) {
            System.out.println("Preprocessing platform library: " + filename);
            preprocessInput(db, tmpDirs, classesInLibraryJars, filename);
        }
        for (String filename : parameters.getDependencies()) {
            System.out.println("Preprocessing dependency: " + filename);
            preprocessInput(db, tmpDirs, classesInDependencyJars, filename);
        }
    }

    /**
     * Preprocess an input archive.
     *
     * @param db         the database object to use
     * @param tmpDirs    the temporary directories set (for clean up)
     * @param classSet   appropriate set to add class names
     * @param filename   the input filename
     */
    private void preprocessInput(Database db, Set<String> tmpDirs,
                                 Collection<String> classSet, String filename) throws IOException {
        String filenameL = filename.toLowerCase();
        boolean isAar = filenameL.endsWith(".aar");
        boolean isJar = filenameL.endsWith(".jar");
        boolean isWar = filenameL.endsWith(".war");
        boolean isZip = filenameL.endsWith(".zip");
        boolean isClass = filenameL.endsWith(".class");
        boolean isApk = filenameL.endsWith(".apk");

        ArtifactScanner.EntryProcessor gProc = (jarFile, entry, entryName) -> {
            if (entryName.endsWith(".properties"))
                propertyProvider.addProperties(jarFile.getInputStream(entry), filename);
            else if ((isJar || isAar || isZip || isWar) && entryName.endsWith(".xml")) {
                // We only handle .xml entries inside JAR archives here.
                // APK archives may contain binary XML and need decoding.
                File xmlTmpFile = ArtifactScanner.extractZipEntryAsFile("xml-file", jarFile, entry, entryName);
                if (parameters._debug)
                    System.out.println("Processing XML entry (in " + filename + "): " + entryName);
                XMLFactGenerator.processFile(xmlTmpFile, db, "", parameters._debug);
            }
        };
        if (isWar) {
            System.out.println("Processing WAR: " + filename);
            // Process WAR inputs.
            parameters.processFatArchives(tmpDirs);
        }
        if (isJar || isApk || isZip || isWar)
            artScanner.processArchive(filename, classSet::add, gProc);
        else if (isClass) {
            File f = new File(filename);
            try (FileInputStream fis = new FileInputStream(f)) {
                artScanner.processClass(fis, f, classSet::add);
            }
        } else
            System.err.println("WARNING: artifact scanner skips " + filename);
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
