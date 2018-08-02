package org.clyze.doop.soot;

import heros.solver.CountingThreadPoolExecutor;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
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
}
