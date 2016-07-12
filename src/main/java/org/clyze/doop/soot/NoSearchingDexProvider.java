package org.clyze.doop.soot;

import org.apache.commons.io.IOUtils;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.iface.ClassDef;
import soot.ClassProvider;
import soot.ClassSource;
import soot.CoffiClassSource;
import soot.dexpler.Util;

import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Created by anantoni on 12/7/2016.
 */
public class NoSearchingDexProvider {
    private Map<String, NoSearchingDexProvider.Resource> _classes;
    private List<ZipFile> _archives;
    private Map<String, Properties> _properties;

    NoSearchingDexProvider() {
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
    List<String> addClasses(File f) throws IOException {
        return addClasses(f.getPath(), new NoSearchingDexProvider.FileResource(f));
    }

    /**
     * Adds a class file in an apk archive. Returns the class name of
     * the class that was added.
     */
    private List<String> addClasses(ZipFile archive, ZipEntry entry) throws IOException {
        return addClasses(entry.getName(), new NoSearchingDexProvider.ZipEntryResource(archive, entry));
    }

    /** Adds a properties file in an apk archive. */
    private void addProperties(ZipFile archive, ZipEntry entry) throws IOException
    {
        addProperties(entry.getName(), new NoSearchingDexProvider.ZipEntryResource(archive, entry));
    }

    /**
     * Adds a class file from a resource.
     */
    private List<String> addClasses(String path, NoSearchingDexProvider.Resource resource) throws IOException {
        InputStream fis = resource.open();
        File tempFile = File.createTempFile("temp", ".tmp");
        tempFile.deleteOnExit();
        FileOutputStream fos = new FileOutputStream(tempFile);
        IOUtils.copy(fis, fos);
        fos.close();
        fis.close();

        DexBackedDexFile d = DexFileFactory.loadDexFile(tempFile, 1, false);
        List<String> result = new ArrayList<>();

        for (ClassDef c : d.getClasses()) {
            String className = Util.dottedClassName(c.getType());
            result.add(className);
            if(_classes.containsKey(className)) {
                throw new RuntimeException(
                        "class " + className + " has already been added to this class provider");
            }
            else {
                _classes.put(className, resource);
            }
        }

        return result;
    }

    /**
     * Adds a properties file from a resource.
     */
    private void addProperties(String path, NoSearchingDexProvider.Resource resource)
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
            if(entry.getName().endsWith(".dex")) {
                List<String> classNames = addClasses(archive, entry);
                result.addAll(classNames);
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
        NoSearchingDexProvider.Resource resource = _classes.get(className);

        if(resource == null) {
            String fileName = className.replace('.', '/') + ".class";

            for(ZipFile archive : _archives) {
                ZipEntry entry = archive.getEntry(fileName);
                if(entry != null) {
                    resource = new NoSearchingDexProvider.ZipEntryResource(archive, entry);
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
                return new CoffiClassSource(className, stream, null, null);
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
        InputStream open() throws IOException;
    }

    /**
     * File.
     */
    private static class FileResource implements NoSearchingDexProvider.Resource {
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
    private static class ZipEntryResource implements NoSearchingDexProvider.Resource {
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
