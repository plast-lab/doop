package org.clyze.doop.common;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

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
    public final Collection<String> xmlRoots = new HashSet<>();

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
        String filenameL = filename.toLowerCase();
        boolean isAar = filenameL.endsWith(".aar");
        boolean isJar = filenameL.endsWith(".jar");
        boolean isZip = filenameL.endsWith(".zip");
        boolean isClass = filenameL.endsWith(".class");
        boolean isApk = filenameL.endsWith(".apk");

        ArtifactScanner.EntryProcessor gProc = (jarFile, entry, entryName) -> {
            File outDir = new File(parameters.getOutputDir());
            if (entryName.endsWith(".properties"))
                propertyProvider.addProperties(jarFile.getInputStream(entry), filename);
            else if ((isJar || isAar || isZip) && entryName.endsWith(".xml")) {
                // We only handle .xml entries inside JAR archives here.
                // APK archives may contain binary XML and need decoding.
                File xmlTmpFile = ArtifactScanner.extractZipEntryAsFile("xml-file", jarFile, entry, entryName);
                if (parameters._debug)
                    System.out.println("Processing XML entry (in " + filename + "): " + entryName);
                XMLFactGenerator.processFile(xmlTmpFile, db, "", parameters._debug);
            }
        };
        if (isJar || isApk || isZip)
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
