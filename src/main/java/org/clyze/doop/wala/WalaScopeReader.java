package org.clyze.doop.wala;

import com.ibm.wala.classLoader.BinaryDirectoryTreeModule;
import com.ibm.wala.classLoader.JarFileModule;
import com.ibm.wala.classLoader.Module;
import com.ibm.wala.classLoader.SourceDirectoryTreeModule;
import com.ibm.wala.dalvik.classLoader.DexFileModule;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.properties.WalaProperties;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.util.config.FileOfClasses;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.io.FileProvider;
import com.ibm.wala.util.strings.Atom;

import java.io.*;
import java.util.StringTokenizer;
import java.util.jar.JarFile;

/*
 * This class is our alternative to WALA's com.ibm.wala.util.config.AnalysisScopeReader
 * Most of the code was taken from that class and modified to suit our needs.
 * We needed to be able to set the path of the Java implementation we will use
 * to produce our facts(the default for wala was to guess it --using $JAVA_HOME
 * unless specified in a specific file, not existing in the jar)
 *
 */
public class WalaScopeReader {
    //The location of WALAprimordial.txt in our resources folder -- file taken from wala's repo
    private static String SCOPE_TEXT_FILE;
    //The location of WALAprimordial.jar.model in our resources folder -- file taken from wala's repo
    //Don't understand what this does but it is needed for some reason
    private static String SCOPE_BIN_FILE;
    //The location of the Java library we want to run our analysis on
    private static String JAVA_LIB_DIR;

    private static final ClassLoader MY_CLASSLOADER = WalaScopeReader.class.getClassLoader();

    static AnalysisScope makeScope(String classPath, File exclusionsFile, String javaLibDir)
    {
        if (classPath == null) {
            throw new IllegalArgumentException("classPath null");
        }
        String myEnv = System.getenv("DOOP_HOME");
        SCOPE_TEXT_FILE = myEnv + "/src/main/resources/WALAprimordial.txt";
        SCOPE_BIN_FILE = myEnv + "/src/main/resources/WALAprimordial.jar.model";
        JAVA_LIB_DIR = javaLibDir;

        AnalysisScope scope = AnalysisScope.createJavaAnalysisScope();
        try {
            read(scope, SCOPE_TEXT_FILE, exclusionsFile, MY_CLASSLOADER);
        } catch (IOException e) {
            e.printStackTrace();
        }
        ClassLoaderReference loader = scope.getLoader(AnalysisScope.APPLICATION);
        addClassPathToScope(classPath, scope, loader);
        return scope;
    }

    public static AnalysisScope setUpAndroidAnalysisScope(String classpath, String exclusions, String androidLib) throws IOException {
        AnalysisScope scope;
        scope = AnalysisScope.createJavaAnalysisScope();

        File exclusionsFile = new File(exclusions);
        try (final InputStream fs = exclusionsFile.exists()? new FileInputStream(exclusionsFile): FileProvider.class.getClassLoader().getResourceAsStream(exclusionsFile.getName())) {
            scope.setExclusions(new FileOfClasses(fs));
        }

        scope.setLoaderImpl(ClassLoaderReference.Primordial,
                "com.ibm.wala.dalvik.classLoader.WDexClassLoaderImpl");


        if(androidLib.endsWith(".apk"))
        {
            scope.addToScope(ClassLoaderReference.Primordial, DexFileModule.make(new File(androidLib)));
        }
        else
        {
            final JarFile jar = new JarFile(new File(androidLib));
            scope.addToScope(ClassLoaderReference.Primordial, new JarFileModule(jar));
        }



        scope.setLoaderImpl(ClassLoaderReference.Application,
                "com.ibm.wala.dalvik.classLoader.WDexClassLoaderImpl");

        scope.addToScope(ClassLoaderReference.Application, DexFileModule.make(new File(classpath)));

        return scope;
    }


    //Method taken from com.ibm.wala.util.config.AnalysisScopeReader
    public static AnalysisScope read(AnalysisScope scope, String scopeFileName, File exclusionsFile, ClassLoader javaLoader) throws IOException {
        BufferedReader r = null;
        try {
            // Now reading from jar is included in WALA, but we can't use their version, because they load from
            // jar by default and use filesystem as fallback. We want it the other way round. E.g. to deliver default
            // configuration files with the jar, but use userprovided ones if present in the working directory.
            // InputStream scopeFileInputStream = fp.getInputStreamFromClassLoader(scopeFileName, javaLoader);
            File scopeFile = new File(scopeFileName);

            String line;
            // assume the scope file is UTF-8 encoded; ASCII files will also be handled properly
            // TODO allow specifying encoding as a parameter?
            if (scopeFile.exists()) {
                r = new BufferedReader(new InputStreamReader(new FileInputStream(scopeFile), "UTF-8"));
            } else {
                // try to read from jar
                InputStream inFromJar = javaLoader.getResourceAsStream(scopeFileName);
                if (inFromJar == null) {
                    throw new IllegalArgumentException("Unable to retrieve " + scopeFileName + " from the jar using " + javaLoader);
                }
                r = new BufferedReader(new InputStreamReader(inFromJar));
            }
            while ((line = r.readLine()) != null) {
                processScopeDefLine(scope, javaLoader, line);
            }

            if (exclusionsFile != null) {
                InputStream fs = exclusionsFile.exists()? new FileInputStream(exclusionsFile): FileProvider.class.getClassLoader().getResourceAsStream(exclusionsFile.getName());
                scope.setExclusions(new FileOfClasses(fs));
            }

        } finally {
            if (r != null) {
                try {
                    r.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return scope;
    }

    //DOOP: We currently only need a fraction of these, but keeping the rest for reference to what it is capable of doing
    private static void processScopeDefLine(AnalysisScope scope, ClassLoader javaLoader, String line) throws IOException {
        if (line == null) {
            throw new IllegalArgumentException("null line");
        }
        StringTokenizer toks = new StringTokenizer(line, "\n,");
        if (!toks.hasMoreTokens()) {
            return;
        }
        Atom loaderName = Atom.findOrCreateUnicodeAtom(toks.nextToken());
        ClassLoaderReference walaLoader = scope.getLoader(loaderName);

        @SuppressWarnings("unused")
        String language = toks.nextToken();
        String entryType = toks.nextToken();
        String entryPathname = toks.nextToken();
        FileProvider fp = (new FileProvider());
        if ("classFile".equals(entryType)) {
            File cf = fp.getFile(entryPathname, javaLoader);
            try {
                scope.addClassFileToScope(walaLoader, cf);
            } catch (InvalidClassFileException e) {
                Assertions.UNREACHABLE(e.toString());
            }
        } else if ("sourceFile".equals(entryType)) {
            File sf = fp.getFile(entryPathname, javaLoader);
            scope.addSourceFileToScope(walaLoader, sf, entryPathname);
        } else if ("binaryDir".equals(entryType)) {
            File bd = fp.getFile(entryPathname, javaLoader);
            assert bd.isDirectory();
            scope.addToScope(walaLoader, new BinaryDirectoryTreeModule(bd));
        } else if ("sourceDir".equals(entryType)) {
            File sd = fp.getFile(entryPathname, javaLoader);
            assert sd.isDirectory();
            scope.addToScope(walaLoader, new SourceDirectoryTreeModule(sd));
        } else if ("jarFile".equals(entryType)) {
            Module M = fp.getJarFileModule(SCOPE_BIN_FILE, javaLoader);
            scope.addToScope(walaLoader, M);
        } else if ("loaderImpl".equals(entryType)) {
            scope.setLoaderImpl(walaLoader, entryPathname);
        } else if ("stdlib".equals(entryType)) {
            //String[] stdlibs = WalaProperties.getJ2SEJarFiles();
            String[] stdlibs = WalaProperties.getJarsInDirectory(JAVA_LIB_DIR); //Gets the jars directly from the directory we set
            for (String stdlib : stdlibs) {
                scope.addToScope(walaLoader, new JarFile(stdlib, false));
            }
        } else {
            Assertions.UNREACHABLE();
        }
    }

    //Method taken from com.ibm.wala.util.config.AnalysisScopeReader
    private static void addClassPathToScope(String classPath, AnalysisScope scope, ClassLoaderReference loader) {
        if (classPath == null) {
            throw new IllegalArgumentException("null classPath");
        }
        try {
            StringTokenizer paths = new StringTokenizer(classPath, File.pathSeparator);
            while (paths.hasMoreTokens()) {
                String path = paths.nextToken();
                if (path.endsWith(".jar")) {
                    JarFile jar = new JarFile(path, false);
                    scope.addToScope(loader, jar);
                    try {
                        if (jar.getManifest() != null) {
                            String cp = jar.getManifest().getMainAttributes().getValue("Class-Path");
                            if (cp != null) {
                                for(String cpEntry : cp.split(" ")) {
                                    addClassPathToScope(new File(path).getParent() + File.separator + cpEntry, scope, loader);
                                }
                            }
                        }
                    } catch (RuntimeException e) {
                        System.err.println("warning: trouble processing class path of " + path);
                    }
                } else {
                    File f = new File(path);
                    if (f.isDirectory()) {
                        scope.addToScope(loader, new BinaryDirectoryTreeModule(f));
                    } else {
                        scope.addClassFileToScope(loader, f);
                    }
                }
            }
        } catch (IOException e) {
            Assertions.UNREACHABLE(e.toString());
        } catch (InvalidClassFileException e) {
            Assertions.UNREACHABLE(e.toString());
        }
    }
}
