package org.clyze.doop.util;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.LinkedList;
import org.apache.log4j.Logger;

/**
 * This class gathers functionality related to the classpath. Some
 * features may not be supported on Java 9+.
 */
public class ClassPathHelper {

    /**
     * Finds a classpath JAR that matches a string. This method can be
     * used to find standalone JARs packaged as dependencies.
     *
     * @param prefix   the prefix of the JAR
     * @return         the path of the JAR
     *
     */
    public static String getClasspathJar(String prefix) {
        ClassLoader cl = ClassPathHelper.class.getClassLoader();
        LinkedList<String> matchingPaths = new LinkedList<>();
        if (cl instanceof URLClassLoader) {
            URL[] classpath = ((URLClassLoader)cl).getURLs();
            String searchString = "/" + prefix;
            for (URL url : classpath) {
                String path = url.getFile();
                if (path.contains(prefix) && path.toLowerCase().endsWith(".jar"))
                    matchingPaths.add(path);
            }
            if (matchingPaths.size() == 1)
                return matchingPaths.get(0);
            else
                throw new RuntimeException("runClasspathJar: no single match for " + prefix + " in classpath: " + Arrays.toString(classpath));
        } else
            throw new RuntimeException("Unsupported classloader " + cl.getClass() + " when running: " + prefix);
    }

    /**
     * Create a copy of the current classpath. Only supported in Java < 9.
     *
     * @param log  a logger to use (may be null)
     * @param obj  an object to use for reading a class loader
     * @return     a new class loader
     */
    public static ClassLoader copyOfCurrentClasspath(Logger log, Object obj) {
        ClassLoader cl = obj.getClass().getClassLoader();
        if (cl instanceof URLClassLoader) {
            if (log != null)
                log.debug("Reading URL entries from current class loader...");
            URL[] classpath = ((URLClassLoader)cl).getURLs();
            if (log != null)
                log.debug("Creating a new URL class loader with classpath = " + Arrays.toString(classpath));
            return new URLClassLoader(classpath, (ClassLoader)null);
        } else {
            return cl;
            // We currently don't support classpath copies for Java 9+. Solution:
            //
            // 1. The classpath can be parsed as follows:
            //   log.debug "Parsing current classpath to reconstruct URL entries..."
            //   String pathSeparator = System.getProperty("path.separator");
            //   classpath = System.getProperty("java.class.path").
            //               split(pathSeparator).
            //               collect { new URL("file://${it}") } as URL[]
            //
            // 2. And then a ModuleLayer must be constructed and loaded:
            //    https://docs.oracle.com/javase/9/docs/api/java/lang/ModuleLayer.html
            //
            // However, the technique above makes Java 9+ a compile-time dependency
            // and thus breaks Java 8 compatibility, unless all code is reflective.
        }
    }
}
