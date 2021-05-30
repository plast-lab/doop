package org.clyze.doop.util;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.function.Consumer;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.clyze.doop.common.DoopErrorCodeException;
import org.clyze.utils.JHelper;

/**
 * A class that provides functionality to find and execute bundled resources
 * (such as standalone programs in JAR form).
 */
public enum Resource {

    SOOT_FACT_GENERATOR("soot-fact-generator.jar", null),
    WALA_FACT_GENERATOR("wala-fact-generator.jar", null),
    DEX_FACT_GENERATOR("dex-fact-generator.jar", null),
    APKTOOL_JAR("apktool-cli-2.4.2.jar", "APKTOOL_PATH"),
    TIMEOUT_UTIL("timeout", null),
    WALA_PRIMORDIAL("WALAprimordial.jar.model", null);

    public final String filename;
    public final String envVarOverride;

    Resource(String filename, String envVarOverride) {
        this.filename = filename;
        this.envVarOverride = envVarOverride;
    }


    // Map from resource id to extracted file.
    private static final EnumMap<Resource, String> resources = new EnumMap<>(Resource.class);

    /**
     * Returns the resource file that corresponds to a resource id. This
     * method lazily extracts files from the bundled Java "resources".
     * @param c        use class to retrieve class loader for locating resources
     * @param logger   a logger object to use
     * @param res      the resource
     * @return         the resource path
     */
    public static String getResource(Class<?> c, Logger logger, Resource res) {
        String ret = resources.get(res);
        if (ret != null)
            return ret;

        String filename = res.filename;
        logger.debug("Initializing resource: " + filename + " with class: " + c.getName());
        ClassLoader loader = c.getClassLoader();
        // Special handling for tools via Java properties (so that Doop can
        // inform fact generators that run as external processes).
        if (res.envVarOverride != null) {
            logger.debug("Checking environment variable for path: " + res.envVarOverride);
            String path = getProperty(res.envVarOverride);
            logger.debug(res.envVarOverride + " = " + path);
            if (path != null) {
                if (path.startsWith("\""))
                    path = path.substring(1);
                if (path.endsWith("\""))
                    path = path.substring(0, path.length() - 1);
                resources.put(res, path);
                logger.debug("Configured resource '" + filename + "' -> " + path);
                return path;
            }
        }
        // Generic handling of bundled resources.
        URL url = loader.getResource(filename);
        logger.debug("Resource URL: " + url);
        if (url == null)
            throw new RuntimeException("ERROR: cannot find resource '" + filename + "'");
        try {
            int dotIdx = filename.lastIndexOf(".");
            String name = dotIdx == -1 ? filename : filename.substring(0, dotIdx);
            String ext = dotIdx == -1 ? "" : filename.substring(dotIdx);
            File resourceFile = File.createTempFile(name, ext);
            resourceFile.deleteOnExit();
            FileUtils.copyURLToFile(url, resourceFile);
            String path = resourceFile.getCanonicalPath();
            resources.put(res, path);
            logger.debug("Resource '" + filename + "' -> " + resourceFile);
            return path;
        } catch (IOException ex) {
            logger.error("ERROR: failed to initialize resource: " + filename);
            ex.printStackTrace();
            return null;
        }
    }

    /**
     * Run a program bundled as a JAR in the resources.
     *
     * @param c            use class to retrieve class loader for locating resources
     * @param logger       the log4j logger to use
     * @param TAG          the tag to use to mark program output
     * @param jvmArgs      the JVM arguments (may be null)
     * @param resource     the resource object
     * @param args         the arguments to pass to the program
     */
    public static void invokeResourceJar(Class<?> c, Logger logger,
                                         String TAG, String[] jvmArgs,
                                         Resource resource, String[] args)
        throws IOException {

        String resourceJar = getResource(c, logger, resource);
        if (resourceJar == null)
            throw new RuntimeException("ERROR: cannot find resource '" + resource + "'");

        logger.debug("Running resource: " + resourceJar);

        // Add extra flags.
        jvmArgs = extraJvmArgs(jvmArgs, getResource(c, logger, APKTOOL_JAR));

        OutputConsumer proc = new OutputConsumer();
        JHelper.runJar(new String[0], jvmArgs, resourceJar, args, TAG, logger.isDebugEnabled(), proc);
        if (proc.error != null)
            throw new RuntimeException(proc.error);
    }

    private static String getProperty(String propName) {
        String prop = System.getProperty(propName);
        if (prop == null)
            return null;
        // Strip quotes from the property value.
        if (prop.startsWith("\""))
            prop = prop.substring(1);
        if (prop.endsWith("\""))
            prop = prop.substring(0, prop.length() - 1);
        return prop;
    }

    private static String[] extraJvmArgs(String[] jvmArgs, String apktoolPath) {
        List<String> l = new ArrayList<>();
        if (jvmArgs != null)
            Collections.addAll(l, jvmArgs);
        if (apktoolPath != null)
            l.add("-DAPKTOOL_PATH=\"" + apktoolPath + "\"");
        return l.toArray(new String[0]);
    }

    @Override
    public String toString() {
        return this.filename;
    }
}

class OutputConsumer implements Consumer<String> {
    public String error = null;
    public void accept(String line) {
        if (line.contains(DoopErrorCodeException.PREFIX)) {
            this.error = line;
        }
    }
}
