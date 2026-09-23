package org.clyze.doop.common;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
                // Key by archive!entry, not just the archive: a jar routinely holds many
                // .properties files (pom.properties, app config, per-profile copies) and
                // keying by archive alone made them clobber each other in the map, so only
                // one survived per jar (e.g. shopizer's config.cms.method was lost).
                propertyProvider.addProperties(jarFile.getInputStream(entry), filename + "!" + entryName);
            else if (entryName.startsWith("meta-inf/services/"))
                // Java service-provider registry (JAR File Spec / java.util.ServiceLoader):
                // the entry name below META-INF/services/ is the service-interface FQN, and
                // each non-comment line names a concrete provider. We emit ServiceProvider
                // facts so the reflective-dispatch logic can bind ServiceLoader.load(iface)
                // results to these providers without any hand-authored registry facts.
                // NB: entryName is lower-cased by ArtifactScanner; read the case-sensitive
                // interface FQN and provider names from entry.getName() / the file body.
                writeServiceProviders(db, jarFile, entry);
            else if (entryName.equals("meta-inf/spring.factories"))
                // Spring's SPI registry (a .properties file): each key is a service
                // interface FQN, its value a comma-separated list of provider classes.
                // Emit one ServiceProvider(key, provider) per value -- same relation as
                // META-INF/services, so the reflective-dispatch logic handles Spring's
                // EnvironmentPostProcessor / ApplicationContextInitializer / Application
                // Listener / FailureAnalyzer providers automatically.
                writeSpringFactories(db, jarFile, entry);
            else if (entryName.startsWith("meta-inf/spring/") && entryName.endsWith(".imports"))
                // Spring Boot 2.7+ imports registry (e.g.
                // META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports):
                // the entry name (minus the .imports suffix) is the registry key and each
                // non-comment line names a registered class. Same list format as
                // META-INF/services -> reuse the same reader with the derived key.
                writeSpringImports(db, jarFile, entry);
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

    /**
     * Parse a META-INF/services/&lt;interface&gt; entry and emit one
     * ServiceProvider(interface, provider) fact per registered provider, following
     * the java.util.ServiceLoader file-format rules: the '#' character starts a
     * comment (to end of line), surrounding whitespace is trimmed, and blank lines
     * are ignored.
     *
     * @param db       the fact database
     * @param jarFile  the enclosing archive
     * @param entry    the META-INF/services/&lt;interface&gt; entry
     */
    private static void writeServiceProviders(Database db, java.util.zip.ZipFile jarFile,
                                              java.util.zip.ZipEntry entry) throws IOException {
        // Case-sensitive interface FQN = the entry name below META-INF/services/.
        String name = entry.getName();
        int slash = name.lastIndexOf("META-INF/services/");
        if (slash < 0)
            return;
        String iface = name.substring(slash + "META-INF/services/".length());
        if (iface.isEmpty() || iface.endsWith("/"))   // directory entry, no interface
            return;
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(jarFile.getInputStream(entry), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                int hash = line.indexOf('#');
                if (hash >= 0)
                    line = line.substring(0, hash);
                line = line.trim();
                if (line.isEmpty())
                    continue;
                db.add(PredicateFile.SERVICE_PROVIDER, iface, line);
            }
        }
    }

    /**
     * Parse META-INF/spring.factories (a Java .properties file) and emit one
     * ServiceProvider(interface, provider) fact per registered provider. Each key
     * is a service-interface FQN and its value is a comma-separated list of
     * provider class names. java.util.Properties handles the '#'/'!' comments and
     * '\' line continuations; we split each value on ',' and trim.
     */
    private static void writeSpringFactories(Database db, java.util.zip.ZipFile jarFile,
                                             java.util.zip.ZipEntry entry) throws IOException {
        Properties props = new Properties();
        try (java.io.InputStream is = jarFile.getInputStream(entry)) {
            props.load(is);
        }
        for (String key : props.stringPropertyNames()) {
            String iface = key.trim();
            if (iface.isEmpty())
                continue;
            for (String provider : props.getProperty(key).split(",")) {
                provider = provider.trim();
                if (!provider.isEmpty())
                    db.add(PredicateFile.SERVICE_PROVIDER, iface, provider);
            }
        }
    }

    /**
     * Parse a META-INF/spring/&lt;key&gt;.imports entry (Spring Boot 2.7+) and emit
     * one ServiceProvider(key, provider) fact per registered class. Same list format
     * as META-INF/services ('#' comments, one class per line); the registry key is
     * the entry name with the .imports suffix stripped.
     */
    private static void writeSpringImports(Database db, java.util.zip.ZipFile jarFile,
                                           java.util.zip.ZipEntry entry) throws IOException {
        String name = entry.getName();                          // case-sensitive
        int slash = name.lastIndexOf("META-INF/spring/");
        if (slash < 0)
            return;
        String key = name.substring(slash + "META-INF/spring/".length());
        if (key.endsWith(".imports"))
            key = key.substring(0, key.length() - ".imports".length());
        if (key.isEmpty())
            return;
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(jarFile.getInputStream(entry), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                int hash = line.indexOf('#');
                if (hash >= 0)
                    line = line.substring(0, hash);
                line = line.trim();
                if (!line.isEmpty())
                    db.add(PredicateFile.SERVICE_PROVIDER, key, line);
            }
        }
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
