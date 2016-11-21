package org.clyze.doop.soot;

import soot.ClassProvider;
import soot.ClassSource;
import soot.asm.AsmClassSource;

import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * This class provider allows the specification of specific .class
 * files for finding classes. Instead of a search path, the specific
 * class files are added to the provider. In this way, it does not
 * matter at all where your classes are: the provider will never
 * return a class it accidentally found on the search path.
 *
 * This provider does allow archives, which are considered in the
 * order in which they've been added to the provider. It does,
 * however, warn if the same class file is added twice.
 */
class NoSearchingClassProvider implements ClassProvider {

    private Map<String, Resource> _classes;
    private List<ZipFile> _archives;
    private Map<String, Properties> _properties;

    NoSearchingClassProvider() {
        _classes = new HashMap<>();
        _archives = new ArrayList<>();
        _properties = new HashMap<>();
    }

    Set<String> getClassNames() {
        return _classes.keySet();
    }

    public Map<String, Properties> getProperties() {
        return _properties;
    }

    /**
     * Adds a class file. Returns the class name of the class that was
     * added.
     */
    String addClass(File f) throws IOException {
        return addClass(f.getPath(), new FileResource(f));
    }

    /**
     * Adds a class file in a zip/jar archive. Returns the class name of
     * the class that was added.
     */
    private String addClass(ZipFile archive, ZipEntry entry) throws IOException {
        return addClass(entry.getName(), new ZipEntryResource(archive, entry));
    }

    /** Adds a properties file in a zip/jar archive. */
    private void addProperties(ZipFile archive, ZipEntry entry) throws IOException
    {
        addProperties(entry.getName(), new ZipEntryResource(archive, entry));
    }

    /**
     * Adds a class file from a resource.
     */
    private String addClass(String path, Resource resource) throws IOException {
        AsmClassSource c = null;
        String className = path.replace('/', '.');

        int suffixIdx = className.lastIndexOf('.');
        // AsmClassSource automatically adds a '.class' extension, so
        // remove it if it exists.
        if (suffixIdx != -1) {
            String suffix = className.substring(suffixIdx, className.length());
            if (suffix.equals(".class"))
                className = className.substring(0, suffixIdx);
            else
                throw new RuntimeException("Class file does not end in .class: " + className);
        }

        InputStream stream = null;
        try {
            stream = resource.open();
            c = new AsmClassSource(path, stream);
        }
        finally {
            if(stream != null) {
                stream.close();
            }
        }

        if(_classes.containsKey(className)) {
            throw new RuntimeException(
                "class " + className + " has already been added to this class provider");
        }
        else {
            _classes.put(className, resource);
        }

        return className;
    }

    /**
     * Adds a properties file from a resource.
     */
    private void addProperties(String path, Resource resource)
        throws IOException
    {
        Properties properties = new Properties();
        InputStream stream = resource.open();

        try {
            properties.load(stream);
        }
        catch (IOException exc) {
            properties.clear();
            properties.loadFromXML(stream);
        }
        finally {
            if(stream != null)
                stream.close();
        }

        _properties.put(path, properties);
    }

    /**
     * Adds an application archive to the class provider.
     */
    List<String> addArchive(File f) throws IOException {
        List<String> result = new ArrayList<>();

        ZipFile archive = new ZipFile(f);
        Enumeration<? extends ZipEntry> entries = archive.entries();
        while(entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if(entry.getName().endsWith(".class")) {
                String className = addClass(archive, entry);
                result.add(className);
            }

            if(entry.getName().endsWith(".properties"))
                addProperties(archive, entry);
        }

        return result;
    }

    /**
     * Adds a library archive to the class provider.
     */
    void addArchiveForResolving(File f) throws IOException {
        _archives.add(new ZipFile(f));
    }

    /**
     * Finds the class for the given className. This method is invoked
     * by the Soot SourceLocator.
     */
    public ClassSource find(String className) {
        Resource resource = _classes.get(className);

        if(resource == null) {
            String fileName = className.replace('.', '/') + ".class";

            for(ZipFile archive : _archives) {
                ZipEntry entry = archive.getEntry(fileName);
                if(entry != null) {
                    resource = new ZipEntryResource(archive, entry);
                    break;
                }
            }
        }

        if(resource == null) {
            return null;
        }
        else {

            try {
                InputStream stream = resource.open();
                //return new CoffiClassSource(className, stream);
                //// (YS) We may need the change below for future Soot versions
                //// (found out by trying a nightly build of Soot).
                // return new CoffiClassSource(className, stream, null, null);
                // (gfour) Use the ASM-equivalent of CoffiClassSource.
                return new AsmClassSource(className, stream);
            }
            catch(IOException exc) {
                throw new RuntimeException(exc);
            }
        }
    }

    /**
     * A resource is something to which we can open an InputStream 1 or
     * more times.
     *
     * Similar to FoundFile in SourceLocator, which is not accessible.
     */
    public static interface Resource {
        public InputStream open() throws IOException;
    }

    /**
     * File.
     */
    private static class FileResource implements Resource {
        private File _file;

        FileResource(File file) {
            super();
            _file = file;
        }

        public InputStream open() throws IOException {
            return new FileInputStream(_file);
        }
    }

    /**
     * Zip file.
     */
    private static class ZipEntryResource implements Resource {
        private ZipFile _archive;
        private ZipEntry _entry;

        ZipEntryResource(ZipFile archive, ZipEntry entry) {
            super();
            _archive = archive;
            _entry = entry;
        }

        public InputStream open() throws IOException {
            return doJDKBugWorkaround(_archive.getInputStream(_entry), _entry.getSize());
        }

        /**
         * Copied from SourceLocator because FoundFile is not accessible
         * outside the soot package.
         */
        private static InputStream doJDKBugWorkaround(InputStream is, long size)
            throws IOException
        {
            int sz = (int) size;
            byte[] buf = new byte[sz];

            final int N = 1024;
            int ln = 0;
            int count = 0;
            while (sz > 0 && (ln = is.read(buf, count, Math.min(N, sz))) != -1)
            {
                count += ln;
                sz -= ln;
            }
            return  new ByteArrayInputStream(buf);
        }
    }
}
