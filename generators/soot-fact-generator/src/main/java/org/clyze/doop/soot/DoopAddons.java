package org.clyze.doop.soot;

import heros.solver.CountingThreadPoolExecutor;

import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.clyze.doop.common.DoopErrorCodeException;
import org.clyze.doop.common.JavaFactWriter;
import soot.PackManager;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Value;
import soot.options.Options;

/**
 * This class gathers modified functionality that Doop needs from Soot.
 */
public class DoopAddons {

    private static Method hc = null;
    private static final String METHODTYPE = "soot.jimple.MethodType";
    private static Class<?> mtClass = null;
    private static Method getRetType = null;
    private static Method getParamTypes = null;
    private static PackManager pm;
    private static Method wC;

    /**
     * Check and load classes before parallel fact generation or
     * synchronized/locking during classloading can cause deadlocks.
     */
    @SuppressWarnings("CatchMayIgnoreException")
    static void initReflectiveAccess() {
        try {
            hc = Class.forName("soot.PolymorphicMethodRef").getDeclaredMethod("handlesClass", String.class);
            hc.setAccessible(true);
        } catch (ClassNotFoundException | NoSuchMethodException ex) { }

        try {
            mtClass = Class.forName(METHODTYPE);
            getRetType = mtClass.getDeclaredMethod("getReturnType");
            getRetType.setAccessible(true);
            getParamTypes = mtClass.getDeclaredMethod("getParameterTypes");
            getParamTypes.setAccessible(true);
        } catch (ClassNotFoundException | NoSuchMethodException ex) { }

        pm = PackManager.v();
        try {
            wC = pm.getClass().getDeclaredMethod("writeClass", SootClass.class);
            wC.setAccessible(true);
        } catch (NoSuchMethodException ex) {
            ex.printStackTrace();
        }
    }

    public static void retrieveAllSceneClassesBodies(Integer _cores) {
        // The old coffi front-end is not thread-safe
        boolean runSeq = (_cores == null) || Options.v().coffi();
        int threadNum = runSeq ? 1 : _cores;
        CountingThreadPoolExecutor executor = new CountingThreadPoolExecutor(threadNum,
                                                                             threadNum, 30, TimeUnit.SECONDS,
                                                                             new LinkedBlockingQueue<>());
        Iterator<SootClass> clIt = Scene.v().getClasses().snapshotIterator();
        while( clIt.hasNext() ) {
            SootClass cl = clIt.next();
            //note: the following is a snapshot iterator;
            //this is necessary because it can happen that phantom methods
            //are added during resolution
            for (SootMethod m : cl.getMethods())
                if (m.isConcrete())
                    executor.execute(m::retrieveActiveBody);
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
        try {
            Method rAB = pm.getClass().getDeclaredMethod("retrieveAllBodies");
            rAB.setAccessible(true);
            rAB.invoke(pm);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
            System.err.println("Could not call Soot method retrieveAllBodies():");
            ex.printStackTrace();
            throw DoopErrorCodeException.error11(ex);
        }
    }

    // Call non-public method: PackManager.v().writeClass(sootClass)
    public static void writeClass(SootClass sootClass) throws DoopErrorCodeException {
        try {
            wC.invoke(pm, sootClass);
        } catch (IllegalAccessException | InvocationTargetException ex) {
            System.err.println("Could not call Soot method writeClass(): ");
            ex.printStackTrace();
            throw DoopErrorCodeException.error12(ex);
        }
    }

    /**
     * Set the "hierachy_dirs" property of Soot to true, so that generated
     * Jimple follows package structure. This handles Jimple generation for
     * very long class names (https://github.com/Sable/soot/pull/1006).
     *
     * @return true if the property could be enabled, false otherwise
     */
    public static boolean checkSetHierarchyDirs() {
        Options opts = Options.v();
        try {
            Method shd = opts.getClass().getDeclaredMethod("set_hierarchy_dirs", boolean.class);
            shd.setAccessible(true);
            shd.invoke(opts, true);
            System.err.println("Soot: hierarchy_dirs set.");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Some versions of Soot do not structure generated Jimple by package, which
     * is expected by the server. This method simulates (so that the server
     * works), but does not address the failing long class names bug (see
     * checkSetHierarchyDirs() above), since it runs after files are written.
     *
     * @param outDir  the directory to be restructured, containing .shimple files
     */
    public static void structureJimpleFiles(String outDir) {
        boolean movedMsg = false;
        String jimpleDirPath = DoopConventions.jimpleDir(outDir);
        File[] jimpleDirFiles = new File(jimpleDirPath).listFiles();
        if (jimpleDirFiles == null) {
            System.err.println("Output directory " + jimpleDirPath + " is empty, cannot restructure Jimple files.");
            return;
        }

        final String JIMPLE_EXT = ".shimple";

        int dirsCreated = 0;
        for (File f : jimpleDirFiles) {
            String fName = f.getName();
            if (fName.endsWith(JIMPLE_EXT)) {
                if (!movedMsg) {
                    System.out.println("Moving " + JIMPLE_EXT + " files to structure under " + jimpleDirPath);
                    movedMsg = true;
                }
                String base = fName.substring(0, fName.length() - JIMPLE_EXT.length()).replace('.', File.separatorChar);
                fName = jimpleDirPath + File.separatorChar + base + JIMPLE_EXT;
                File newFile = new File(fName);
                if (newFile.getParentFile().mkdirs())
                    dirsCreated++;
                try {
                    Files.move(f.toPath(), newFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ex) {
                    System.err.println("Error moving " + f);
                    ex.printStackTrace();
                }
            }
        }
        if (dirsCreated > 0)
            System.out.println("Jimple output restructured, created " + dirsCreated + " directories.");
    }

    /**
     * Returns true if Doop uses the upstream version of Soot, false if it uses the fork.
     */
    public static boolean usingUpstream() {
        try {
            Objects.requireNonNull(Class.forName("soot.jimple.toolkits.scalar.DoopRenamer"));
            return false;
        } catch (ClassNotFoundException ex) {
            return true;
        }
    }

    private static boolean polymorphicHandling_msg = false;
    @SuppressWarnings("CatchMayIgnoreException")
    public static boolean polymorphicHandling(String declClass, String simpleName) {
        try {
            if (hc != null)
                return (boolean)hc.invoke(null, declClass);
        } catch (IllegalAccessException | InvocationTargetException ex) { }

        if (!polymorphicHandling_msg) {
            polymorphicHandling_msg = true;
            System.err.println("WARNING: Soot does not contain PolymorphicMethodRef.handlesClass(), using custom method.");
        }
        return JavaFactWriter.polymorphicHandling(declClass, simpleName);
    }

    /**
     * Since MethodType was introduced in Soot 3.2, to maintain
     * compatibility with earlier versions, we introduce a reflective
     * layer and our own custom MethodType class.
     */
    @SuppressWarnings("CatchMayIgnoreException")
    public static MethodType methodType(Value v) {
        if (!(METHODTYPE.equals(v.getClass().getName())))
            return null;
        try {
            if (mtClass != null) {
                // Dynamic instanceof check.
                Object methodType = mtClass.cast(v);
                String retType = getRetType.invoke(methodType).toString();
                Object paramTypesObj = getParamTypes.invoke(methodType);
                if (!(paramTypesObj instanceof List))
                    return null;
                Iterable<?> paramTypesT = (List<?>)paramTypesObj;
                List<String> paramTypes = new LinkedList<>();
                for (Object t : paramTypesT)
                    paramTypes.add(t.toString());
                return new MethodType(retType, paramTypes);
            }
        } catch (IllegalAccessException | InvocationTargetException ex) { }
        return null;
    }

    public static class MethodType {
	final String returnType;
	final List<String> parameterTypes;
	MethodType(String retType, List<String> paramTypes) {
	    this.returnType = retType;
	    this.parameterTypes = paramTypes;
	}
	public String getReturnType() {
	    return returnType;
	}
	public List<String> getParameterTypes() {
	    return parameterTypes;
	}
    }
}
