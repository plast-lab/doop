package org.clyze.doop.wala;

import com.ibm.wala.classLoader.JarFileModule;
import com.ibm.wala.classLoader.Module;
import com.ibm.wala.dalvik.classLoader.DexFileModule;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.util.config.FileOfClasses;
import com.ibm.wala.util.io.FileProvider;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.iface.MultiDexContainer;

import java.io.*;
import java.util.jar.JarFile;

/*
 * This class is our alternative to WALA's com.ibm.wala.util.config.AnalysisScopeReader
 * Most of the code was taken from that class and modified to suit our needs.
 * We needed to be able to set the path of the Java implementation we will use
 * to produce our facts(the default for wala was to guess it --using $JAVA_HOME
 * unless specified in a specific file, not existing in the jar)
 *
 */
class WalaScopeReader {

    private static final ClassLoader MY_CLASSLOADER = WalaScopeReader.class.getClassLoader();

    static AnalysisScope setupJavaAnalysisScope(Iterable<String> inputJars, String exclusions, Iterable<String> javaLibs, Iterable<String> appLibs) throws IOException
    {
        String myEnv = System.getenv("DOOP_HOME");
        //The location of WALAprimordial.jar.model in our resources folder -- file taken from wala's repo
        //Don't understand what this does but it is needed for some reason
        String SCOPE_BIN_FILE = myEnv + "/src/main/resources/WALAprimordial.jar.model";
        AnalysisScope scope = AnalysisScope.createJavaAnalysisScope();

        for(String javaLib : javaLibs) {
            final JarFile jar = new JarFile(new File(javaLib));
            scope.addToScope(ClassLoaderReference.Primordial, new JarFileModule(jar));
        }

        Module M = (new FileProvider()).getJarFileModule(SCOPE_BIN_FILE, MY_CLASSLOADER);
        scope.addToScope(ClassLoaderReference.Primordial, M);

        for(String appLib : appLibs) {
            final JarFile jar = new JarFile(new File(appLib));
            scope.addToScope(ClassLoaderReference.Extension, new JarFileModule(jar));
        }


        for(String input : inputJars)
        {
            JarFile jar = new JarFile(input, false);
            scope.addToScope(scope.getLoader(AnalysisScope.APPLICATION), jar);
        }

        //String[] inputJars = classPath.split(":");
        //addClassPathToScope(classPath, scope, scope.getLoader(AnalysisScope.APPLICATION));
        return scope;
    }

    public static AnalysisScope setUpAndroidAnalysisScope(Iterable<String> inputs, String exclusions, Iterable<String> androidLibs, Iterable<String> appLibs) throws IOException {
        AnalysisScope scope;
        scope = AnalysisScope.createJavaAnalysisScope();

        File exclusionsFile = new File(exclusions);
        try (final InputStream fs = exclusionsFile.exists()? new FileInputStream(exclusionsFile): FileProvider.class.getClassLoader().getResourceAsStream(exclusionsFile.getName())) {
            scope.setExclusions(new FileOfClasses(fs));
        }

        scope.setLoaderImpl(ClassLoaderReference.Primordial,
                "com.ibm.wala.dalvik.classLoader.WDexClassLoaderImpl");

        for(String androidLib : androidLibs) {
            if (androidLib.endsWith(".apk")) {
                scope.addToScope(ClassLoaderReference.Primordial, DexFileModule.make(new File(androidLib)));
            } else {
                final JarFile jar = new JarFile(new File(androidLib));
                scope.addToScope(ClassLoaderReference.Primordial, new JarFileModule(jar));
            }
        }

        scope.setLoaderImpl(ClassLoaderReference.Extension,
                "com.ibm.wala.dalvik.classLoader.WDexClassLoaderImpl");

        for(String appLib : appLibs) {
            if (appLib.endsWith(".apk")) {
                scope.addToScope(ClassLoaderReference.Extension, DexFileModule.make(new File(appLib)));
            } else {
                final JarFile jar = new JarFile(new File(appLib));
                scope.addToScope(ClassLoaderReference.Extension, new JarFileModule(jar));
            }
        }


        scope.setLoaderImpl(ClassLoaderReference.Application,
                "com.ibm.wala.dalvik.classLoader.WDexClassLoaderImpl");

        for(String input: inputs)
            addAPKtoScope(ClassLoaderReference.Application, scope, input);
            //scope.addToScope(ClassLoaderReference.Application, DexFileModule.make(new File(input)));

        return scope;
    }

    private static void addAPKtoScope(ClassLoaderReference loader, AnalysisScope scope, String fileName){
        File apkFile = new File(fileName);
        MultiDexContainer<? extends DexBackedDexFile> multiDex = null;
        final int API = 24;
        try {
            multiDex = DexFileFactory.loadDexContainer(apkFile, Opcodes.forApi(API));
            for (String dexEntry : multiDex.getDexEntryNames()) {
                System.out.println("Adding dex file: " +dexEntry + " of file:" + fileName);
                scope.addToScope(loader, new DexFileModule(apkFile, dexEntry, API));
            }
        } catch (IOException e){
            System.err.println("Failed to open " +fileName + " as multidex container.\n" + e);
        }
    }
}
