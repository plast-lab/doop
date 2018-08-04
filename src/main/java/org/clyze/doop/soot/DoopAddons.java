package org.clyze.doop.soot;

import heros.solver.CountingThreadPoolExecutor;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Iterator;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import soot.PackManager;
import soot.Scene;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.options.Options;

/**
 * This class gathers modified functionality that Doop needs from Soot.
 */
public class DoopAddons {

    private static boolean getInitialValueString_warned = false;
    private static boolean foundFile_warned = false;

    public static void retrieveAllSceneClassesBodies() {
        // The old coffi front-end is not thread-safe
        int threadNum = Options.v().coffi() ? 1 : Runtime.getRuntime().availableProcessors();
        CountingThreadPoolExecutor executor =  new CountingThreadPoolExecutor(threadNum,
                                                                              threadNum, 30, TimeUnit.SECONDS,
                                                                              new LinkedBlockingQueue<>());
        Iterator<SootClass> clIt = Scene.v().getClasses().snapshotIterator();
        while( clIt.hasNext() ) {
            SootClass cl = clIt.next();
            //note: the following is a snapshot iterator;
            //this is necessary because it can happen that phantom methods
            //are added during resolution
            Iterator<SootMethod> methodIt = cl.getMethods().iterator();
            while (methodIt.hasNext()) {
                final SootMethod m = methodIt.next();
                if( m.isConcrete() ) {
                    executor.execute(new Runnable() {
                            @Override
                            public void run() {
                                m.retrieveActiveBody();
                            }
                        });
                }
            }
        }
        // Wait till all method bodies have been loaded
        try {
            executor.awaitCompletion();
            executor.shutdown();
        } catch (InterruptedException e) {
            // Something went horribly wrong
            throw new RuntimeException("Could not wait for loader threads to "
                                       + "finish: " + e.getMessage(), e);
        }
        // If something went wrong, we tell the world
        if (executor.getException() != null)
            throw (RuntimeException) executor.getException();
    }

    // Call non-public method: PackManager.v().retrieveAllBodies()
    public static void retrieveAllBodies() throws DoopErrorCodeException {
        PackManager pm = PackManager.v();
        try {
            Method rAB = pm.getClass().getDeclaredMethod("retrieveAllBodies", new Class[] { });
            rAB.setAccessible(true);
            rAB.invoke(pm, new Object[] { });
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
            System.err.println("Could not call Soot method retrieveAllBodies():");
            ex.printStackTrace();
            throw new DoopErrorCodeException(11);
        }
    }

    // Call non-public method: PackManager.v().writeClass(sootClass)
    public static void writeClass(SootClass sootClass) throws DoopErrorCodeException {
        PackManager pm = PackManager.v();
        try {
            Method wC = pm.getClass().getDeclaredMethod("writeClass", new Class[] { SootClass.class });
            wC.setAccessible(true);
            wC.invoke(pm, new Object[] { sootClass });
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
            System.err.println("Could not call Soot method writeClass(): ");
            ex.printStackTrace();
            throw new DoopErrorCodeException(12);
        }
    }

    /**
     * Calls the handler for field initial values (useful for Android
     * apps), return null when this functionality is not available.
     */
    public static String getInitialValueString(SootField f) {
        try {
            Method gIVS = f.getClass().getDeclaredMethod("getInitialValueString", new Class[] { });
            return (String) gIVS.invoke(f, new Object[] { });
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
            if (!getInitialValueString_warned) {
                System.err.println("Warning: SootField method getInitialValueString() is not available.");
                getInitialValueString_warned = true;
            }
            return null;
        }
    }

    /**
     * Creates an instance of class "FoundFile" (which may exist in different
     * locations in the class hierarchy between our Soot fork and upstream).
     */
    public static FoundFile newFoundFile(String archivePath, String entryName) {
        Class<?> foundFileClass;

        // Resolve FoundFile class dynamically.
        try {
            foundFileClass = Class.forName("soot.FoundFile");
        } catch (ClassNotFoundException ex1) {
            try {
                foundFileClass = Class.forName("soot.SourceLocator$FoundFile");
            } catch (ClassNotFoundException ex2) {
                System.out.println("Error: cannot find class FoundFile.");
                return null;
            }
        }

        if (!foundFile_warned) {
            System.err.println("Using Soot class: " + foundFileClass.getName());
            foundFile_warned = true;
        }

        // Construct an instance.
        try {
            Constructor<?> ctr = foundFileClass.getConstructor(new Class[] {String.class, String.class});
            Object ff = ctr.newInstance(archivePath, entryName);
            return new FoundFile(ff);
        } catch (NoSuchMethodException | InstantiationException | InvocationTargetException | IllegalAccessException ex) {
            ex.printStackTrace();
            return null;
        }
    }

    // The part of FoundFile that we wrap.
    static class FoundFile {
        Object ff;

        public FoundFile(Object ff) { this.ff = ff; }

        private Object nullaryCall(String mName) {
            try {
                Method m = ff.getClass().getDeclaredMethod(mName, new Class[] { });
                return m.invoke(ff);
            } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException ex) {
                ex.printStackTrace();
                return null;
            }
        }

        InputStream inputStream() {
            return (InputStream) nullaryCall("inputStream");
        }

        String getFilePath() {
            return (String) nullaryCall("getFilePath");
        }
    }

    /**
     * Upstream Soot does not structure generated Jimple by package, which is
     * expected by the server.
     */
    public static void structureJimpleFiles(String outDir) {
        boolean movedMsg = false;
        String jimpleDirPath = outDir + File.separatorChar + "jimple";
        File[] outDirFiles = new File(outDir).listFiles();

        final String JIMPLE_EXT = ".shimple";

        for (File f : outDirFiles) {
            String fName = f.getName();
            if (fName.endsWith(JIMPLE_EXT)) {
                if (!movedMsg) {
                    System.out.println("Moving " + JIMPLE_EXT + " files to structure under " + jimpleDirPath);
                    movedMsg = true;
                }
                String base = fName.substring(0, fName.length() - JIMPLE_EXT.length()).replace('.', File.separatorChar);
                fName = jimpleDirPath + File.separatorChar + base + JIMPLE_EXT;
                File newFile = new File(fName);
                newFile.getParentFile().mkdirs();
                try {
                    Files.move(f.toPath(), newFile.toPath());
                } catch (IOException ex) {
                    System.err.println("Error moving " + f);
                    ex.printStackTrace();
                }
            }
        }
    }
}
