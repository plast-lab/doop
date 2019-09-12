package org.clyze.doop.util;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

import org.apache.log4j.Logger;

/**
 * This class gathers functionality related to the classpath. Some
 * features may not be supported on Java 9+.
 */
public class ClassPathHelper {

    /**
     * Finds a classpath JAR that matches a string. This method can be
     * used to find standalone JARs packaged as dependencies. This
     * method reads the current classloader classpath and is thus only
     * supported in Java < 9.
     *
     * @param prefix   the prefix of the JAR
     * @return         the path of the JAR
     *
     */
    public static String getClasspathJar(String prefix) {
        ClassLoader cl = ClassPathHelper.class.getClassLoader();
        if (cl instanceof URLClassLoader) {
            Collection<String> classpath = new HashSet<>();
            URL[] urls = ((URLClassLoader)cl).getURLs();
            for (URL url : urls)
                classpath.add(url.getFile());
            return getClasspathJar(prefix, classpath);
        } else
            throw new RuntimeException("Could not handle non-URLClassloader.");
    }

    /**
     * Finds a classpath JAR that matches a string.
     *
     * @param prefix     the prefix of the JAR
     * @param classpath  the classpath
     * @return           the path of the JAR
     *
     */
    private static String getClasspathJar(String prefix, Iterable<String> classpath) {
	final String searchString = "/" + prefix;
        LinkedList<String> matchingPaths = new LinkedList<>();
	for (String path : classpath) {
	    if (path.contains(prefix) && path.toLowerCase().endsWith(".jar"))
		matchingPaths.add(path);
	}
	int matches = matchingPaths.size();
	if (matches == 1)
	    return matchingPaths.get(0);
	else if (matches > 1)
	    throw new RuntimeException("No single match for '" + prefix + "' in classpath: " + String.join(":", classpath));
	else
	    throw new RuntimeException("Could not find classpath entry: " + prefix);
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
