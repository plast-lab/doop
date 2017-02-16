package org.clyze.doop.soot;

import org.objectweb.asm.ClassReader;

import soot.ClassProvider;
import soot.ClassSource;
import soot.SourceLocator;
import soot.asm.AsmClassSource;

import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static soot.SourceLocator.*;

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

    private Map<String, FoundFile> _classes;
    private Map<String, ZipFile> _archives;
    private Map<String, Properties> _properties;

    NoSearchingClassProvider() {
        _classes = new HashMap<>();
        _archives = new HashMap<>();
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
        return addClass(new FoundFile(f));
    }

    /**
     * Adds a class file from a resource.
     */
    private String addClass(FoundFile foundFile) throws IOException
    {
        try ( InputStream stream = foundFile.inputStream() ) {
            // Get class name by reading class contents
            ClassReader classReader = new ClassReader(stream);
            String className = classReader.getClassName().replace("/", ".");

            // Sanity check
            if (_classes.containsKey(className)) {
                //throw new IllegalStateException(
                System.err.println("Class " + className + " has already been added to this class provider");
            }

            // Store class resource
            _classes.put(className, foundFile);
            return className;
        }
    }

    /**
     * Adds a properties file from a resource.
     */
    private void addProperties(FoundFile foundFile)
        throws IOException
    {
        Properties properties = new Properties();
        InputStream stream = foundFile.inputStream();

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

        _properties.put(foundFile.getFilePath(), properties);
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
                String className = addClass(new FoundFile(f.getPath(), entry.getName()));
                result.add(className);
            }

            if(entry.getName().endsWith(".properties"))
                addProperties(new FoundFile(f.getPath(), entry.getName()));
        }

        return result;
    }

    /**
     * Adds a library archive to the class provider.
     */
    void addArchiveForResolving(File f) throws IOException {
        _archives.put(f.getPath(), new ZipFile(f));
    }

    /**
     * Finds the class for the given className. This method is invoked
     * by the Soot SourceLocator.
     */
    public ClassSource find(String className) {

        FoundFile foundFile = _classes.get(className);

        if(foundFile == null) {
            String fileName = className.replace('.', '/') + ".class";

            for(String path : _archives.keySet()) {
                ZipFile archive = _archives.get(path);
                ZipEntry entry = archive.getEntry(fileName);
                if(entry != null) {
                    foundFile = new FoundFile(path, entry.getName());
                    break;
                }
            }
        }

        if(foundFile == null) {
            return null;
        }
        else {
            return new AsmClassSource(className, foundFile);
        }
    }
}
