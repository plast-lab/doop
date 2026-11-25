package org.clyze.doop.core

import groovy.transform.CompileStatic
import groovy.util.logging.Log4j
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.apache.commons.io.FilenameUtils

/**
 * Turns deployable archives into the class path their class loader sees.
 *
 * <p>A web application archive (.war) is loaded by one webapp class loader
 * over WEB-INF/classes and the jars of WEB-INF/lib (Servlet spec, "Web
 * Application Class Loader"). A Spring Boot executable jar is loaded by one
 * LaunchedURLClassLoader over BOOT-INF/classes and the jars of BOOT-INF/lib,
 * in BOOT-INF/classpath.idx order. In both cases classes live under a prefix
 * and libraries are nested jars, which neither JPhantom nor the fact
 * generator read, so each deployable is replaced by:
 * <ul>
 * <li>a jar of its classes, re-rooted as the class loader sees them, plus the
 * archive's other resources at their own paths (web.xml, Spring XML,
 * META-INF), named {@code <archive file name>.classes.jar}; it takes the
 * archive's place (input or library);</li>
 * <li>its nested jars, extracted under their own file names, as libraries,
 * in class path order.</li>
 * </ul>
 * The classes of the archive that no application loader sees (the Spring
 * Boot launcher at the root of an executable archive) are left out and
 * counted. Archives that need several class loaders (OSGi bundles, plugin
 * jars with embedded libraries, EARs) are not modelled: one class path has
 * one class per name, and the classes defined by more than one jar of the
 * result are reported.
 */
@Log4j
class DeployableArchives {

    static class Result {
        List<File> inputs
        List<File> libraries
        int unpacked
    }

    /** The kind of a deployable archive, or null for a plain jar. */
    static String kind(File f) {
        String ext = FilenameUtils.getExtension(f.name).toLowerCase()
        if (!f.isFile() || !['war', 'jar'].contains(ext))
            return null
        new ZipFile(f).withCloseable { ZipFile z ->
            boolean war = false, boot = false
            for (ZipEntry e : Collections.list(z.entries())) {
                String n = e.name
                if (n.startsWith('WEB-INF/classes/') || n.startsWith('WEB-INF/lib/'))
                    war = true
                else if (n.startsWith('BOOT-INF/classes/') || n.startsWith('BOOT-INF/lib/'))
                    boot = true
            }
            if (war || ext == 'war')
                return 'war'
            return boot ? 'boot' : null
        } as String
    }

    static Result unpack(List<File> inputs, List<File> libraries, File outDir) {
        File dir = new File(outDir, 'deployables')
        Result r = new Result(inputs: [], libraries: [], unpacked: 0)
        List<File> nestedLibs = []
        Closure<File> one = { File f ->
            String k = kind(f)
            if (k == null)
                return f
            File d = new File(dir, (r.unpacked++).toString())
            UnpackedArchive u = unpackOne(f, k, d)
            nestedLibs.addAll(u.libraries)
            return u.classes
        }
        r.inputs = inputs.collect(one).findAll { it != null }
        r.libraries = libraries.collect(one).findAll { it != null } + nestedLibs
        if (r.unpacked > 0)
            reportDuplicates(r.inputs + r.libraries)
        return r
    }

    static class UnpackedArchive {
        File classes
        List<File> libraries = []
    }

    private static UnpackedArchive unpackOne(File archive, String kind, File d) {
        String classesPrefix = kind == 'war' ? 'WEB-INF/classes/' : 'BOOT-INF/classes/'
        List<String> libPrefixes = kind == 'war' ? ['WEB-INF/lib/', 'WEB-INF/lib-provided/'] : ['BOOT-INF/lib/']
        UnpackedArchive u = new UnpackedArchive()
        File libDir = new File(d, 'lib')
        libDir.mkdirs()
        int skippedClasses = 0
        int resources = 0
        int classes = 0
        Map<String, File> libsByEntry = [:]
        new ZipFile(archive).withCloseable { ZipFile z ->
            List<ZipEntry> entries = z.entries().toList().findAll { !it.directory }.sort { it.name }
            // the classes jar: re-rooted classes + the other resources
            u.classes = new File(d, archive.name + '.classes.jar')
            Set<String> written = [] as Set
            new ZipOutputStream(new FileOutputStream(u.classes)).withCloseable { ZipOutputStream zout ->
                Closure put = { String name, ZipEntry e ->
                    if (!written.add(name))
                        return
                    ZipEntry ze = new ZipEntry(name)
                    ze.time = 0L
                    zout.putNextEntry(ze)
                    z.getInputStream(e).withCloseable { zout << it }
                    zout.closeEntry()
                }
                for (ZipEntry e : entries) {
                    String n = e.name
                    if (n.startsWith(classesPrefix)) {
                        String rel = n.substring(classesPrefix.length())
                        if (rel.endsWith('.class'))
                            classes++
                        put(rel, e)
                    }
                }
                for (ZipEntry e : entries) {
                    String n = e.name
                    if (n.startsWith(classesPrefix) || libPrefixes.any { n.startsWith(it) })
                        continue
                    if (n.endsWith('.class')) {
                        // not on the application class path (e.g. the Boot launcher)
                        skippedClasses++
                        continue
                    }
                    if (kind == 'boot' && n.startsWith('BOOT-INF/'))
                        continue
                    resources++
                    if (n == 'META-INF/MANIFEST.MF') {
                        putManifest(zout, z, e, kind, archive)
                        written << n
                    } else
                        put(n, e)
                }
            }
            // the nested jars, under their own file names
            for (ZipEntry e : entries) {
                String prefix = libPrefixes.find { e.name.startsWith(it) }
                if (prefix == null || !e.name.toLowerCase().endsWith('.jar'))
                    continue
                String rel = e.name.substring(prefix.length())
                if (rel.contains('/')) {
                    log.warn "WARNING: ${archive.name}: ${e.name} is not directly in ${prefix}, not on the class path"
                    continue
                }
                File sub = new File(libDir, prefix == 'WEB-INF/lib-provided/' ? 'provided' : 'lib')
                sub.mkdirs()
                File out = new File(sub, rel)
                z.getInputStream(e).withCloseable { InputStream is -> out.withOutputStream { it << is } }
                libsByEntry[e.name] = out
            }
            u.libraries = classPathOrder(z, kind, libsByEntry)
        }
        log.info "Deployable ${archive.name} (${kind == 'war' ? 'web application' : 'Spring Boot executable'}): ${classes} classes and ${resources} other resources -> ${u.classes.name}, ${u.libraries.size()} nested jars as libraries" +
                 (skippedClasses ? ", ${skippedClasses} classes outside the application class path left out" : "")
        return u
    }

    /**
     * The main class that starts the given input: a Boot jar's Start-Class
     * (its Main-Class is the launcher), none for a war (its container starts
     * it), else the manifest's Main-Class.
     */
    static String entryPoint(File f, java.util.jar.Manifest mf) {
        java.util.jar.Attributes a = mf?.mainAttributes
        if (a == null)
            return null
        String k = kind(f)
        if (k == 'war')
            return null
        if (k == 'boot')
            return a.getValue('Start-Class')
        return a.getValue('Main-Class')
    }

    /**
     * The archive's manifest, with the entry point its launcher uses: a Boot
     * jar's Main-Class is the Boot launcher, which runs Start-Class; a war is
     * started by its container, not by a main method.
     */
    private static void putManifest(ZipOutputStream zout, ZipFile z, ZipEntry e, String kind, File archive) {
        java.util.jar.Manifest mf = z.getInputStream(e).withCloseable { new java.util.jar.Manifest(it) }
        java.util.jar.Attributes a = mf.mainAttributes
        String launcher = a.getValue('Main-Class')
        String start = a.getValue('Start-Class')
        if (kind == 'boot' && start) {
            a.putValue('Main-Class', start)
            log.info "Deployable ${archive.name}: entry point Start-Class ${start} (launcher ${launcher})"
        } else if (launcher) {
            a.remove(java.util.jar.Attributes.Name.MAIN_CLASS)
            log.info "Deployable ${archive.name}: launcher Main-Class ${launcher} dropped" + (kind == 'boot' ? " (no Start-Class)" : " (a web application is started by its container)")
        }
        ZipEntry ze = new ZipEntry(e.name)
        ze.time = 0L
        zout.putNextEntry(ze)
        mf.write(zout)
        zout.closeEntry()
    }

    /** Boot: BOOT-INF/classpath.idx order, then the rest by name. War: by name (the order is unspecified). */
    private static List<File> classPathOrder(ZipFile z, String kind, Map<String, File> libsByEntry) {
        List<String> order = []
        if (kind == 'boot') {
            ZipEntry idx = z.getEntry('BOOT-INF/classpath.idx')
            if (idx != null) {
                z.getInputStream(idx).withCloseable { InputStream is ->
                    is.newReader('UTF-8').eachLine { String line ->
                        // - "BOOT-INF/lib/foo.jar"
                        def m = line =~ /^- "(.*)"$/
                        if (m.matches() && libsByEntry.containsKey(m.group(1)))
                            order << m.group(1)
                    }
                }
            } else
                log.warn "WARNING: no BOOT-INF/classpath.idx, nested jars taken in name order"
        }
        List<String> rest = (libsByEntry.keySet() - order).sort()
        if (kind == 'war') {
            // lib-provided after lib: what the container provides
            rest = rest.findAll { it.startsWith('WEB-INF/lib/') } + rest.findAll { !it.startsWith('WEB-INF/lib/') }
        }
        return (order + rest).collect { libsByEntry[it] }
    }

    /** Reports the classes that more than one jar defines (one class path holds one class per name). */
    @CompileStatic
    static void reportDuplicates(List<File> jars) {
        Map<String, List<String>> owners = new TreeMap<>()
        for (File f : jars) {
            if (!f.isFile() || !f.name.toLowerCase().endsWith('.jar'))
                continue
            ZipFile z = new ZipFile(f)
            Set<String> seen = new HashSet<>()
            try {
                for (ZipEntry e : Collections.list(z.entries())) {
                    String n = e.name
                    if (!n.endsWith('.class') || n.startsWith('META-INF/') || n.endsWith('module-info.class'))
                        continue
                    List<String> js = owners.computeIfAbsent(n, { k -> new ArrayList<String>() })
                    // a jar may hold an entry twice: it still defines the class once
                    if (seen.add(n))
                        js.add(f.name)
                }
            } finally {
                z.close()
            }
        }
        Map<String, Integer> pairs = new TreeMap<>()
        int dups = 0
        owners.each { String cls, List<String> js ->
            if (js.size() > 1) {
                dups++
                String key = js.join(' / ')
                pairs.put(key, (pairs.get(key) ?: 0) + 1)
            }
        }
        if (dups == 0) {
            log.info "Class path: no class is defined by more than one jar"
            return
        }
        log.warn "WARNING: class path: ${dups} classes are defined by more than one jar in ${pairs.size()} jar combinations:"
        pairs.entrySet().sort { a, b -> b.value <=> a.value ?: a.key <=> b.key }.take(20).each {
            log.warn "  ${it.value} classes: ${it.key}"
        }
    }
}
