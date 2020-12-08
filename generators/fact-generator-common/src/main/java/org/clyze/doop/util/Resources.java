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
public class Resources {

    public static final String SOOT_FACT_GENERATOR = "soot-fact-generator.jar";
    public static final String WALA_FACT_GENERATOR = "wala-fact-generator.jar";
    public static final String DEX_FACT_GENERATOR = "dex-fact-generator.jar";
    public static final String APKTOOL_JAR = "apktool-cli-2.4.2.jar";
    // Map from resource id to extracted file.
    private static final Map<String, File> resources = new HashMap<>();

    /**
     * Returns the resource file that corresponds to a resource id. This
     * method lazily extracts files from the bundled Java "resources".
     * @param logger        a logger object to use
     * @param filename      the filename of the resource
     * @return              the resource file
     */
    private static File getResourceJAR(Logger logger, String filename) {
        File ret = resources.get(filename);
        if (ret != null)
            return ret;

        logger.debug("Initializing JAR resource: " + filename);
        // Special handling for apktool via Java property (so that Doop can
        // inform fact generators that run as external processes).
        if (filename.equals(APKTOOL_JAR)) {
            String apktoolPath = getApktoolPathProperty();
            if (apktoolPath != null) {
                File apktool = new File(apktoolPath);
                resources.put(filename, apktool);
                logger.debug("Configured resource '" + filename + "' -> " + apktoolPath);
                return apktool;
            }
        }
        URL url = Resources.class.getClassLoader().getResource(filename);
        if (url == null)
            throw new RuntimeException("ERROR: cannot find resource '" + filename + "'");
        try {
            File resourceJar = File.createTempFile(filename, ".jar");
            resourceJar.deleteOnExit();
            FileUtils.copyURLToFile(url, resourceJar);
            resources.put(filename, resourceJar);
            logger.debug("Resource '" + filename + "' -> " + resourceJar);
            return resourceJar;
        } catch (IOException ex) {
            logger.error("ERROR: failed to initialize resource: " + filename);
            ex.printStackTrace();
            return null;
        }
    }

    /**
     * Run a program bundled as a JAR in the resources.
     *
     * @param logger       the log4j logger to use
     * @param TAG          the tag to use to mark program output
     * @param jvmArgs      the JVM arguments (may be null)
     * @param resource     the prefix of the resource JAR (should match one resource)
     * @param args         the arguments to pass to the program
     */
    public static void invokeResourceJar(Logger logger, String TAG, String[] jvmArgs, String resource, String[] args)
        throws IOException {

        File resourceJar = getResourceJAR(logger, resource);
        if (resourceJar == null)
            throw new RuntimeException("ERROR: cannot find resource '" + resource + "'");

        logger.debug("Running resource: " + resourceJar);

        // Add extra flags.
        jvmArgs = extraJvmArgs(jvmArgs, getResourceJAR(logger, APKTOOL_JAR));

        OutputConsumer proc = new OutputConsumer();
        JHelper.runJar(new String[0], jvmArgs, resourceJar.getCanonicalPath(), args, TAG, logger.isDebugEnabled(), proc);
        if (proc.error != null)
            throw new RuntimeException(proc.error);
    }

    private static String getApktoolPathProperty() {
        String prop = System.getProperty("APKTOOL_PATH");
        if (prop == null)
            return null;
        // Strip quotes from the property value.
        if (prop.startsWith("\""))
            prop = prop.substring(1);
        if (prop.endsWith("\""))
            prop = prop.substring(0, prop.length() - 1);
        return prop;
    }

    private static String[] extraJvmArgs(String[] jvmArgs, File apktool) {
        if (jvmArgs == null)
            jvmArgs = new String[0];

        if (apktool == null)
            return jvmArgs;

        int extraArgs = 1;
        String[] newJvmArgs = new String[jvmArgs.length + extraArgs];
        try {
            // Pass apktool location to called program via system property.
            newJvmArgs[0] = "-DAPKTOOL_PATH=\"" + apktool.getCanonicalPath() + "\"";
            System.arraycopy(jvmArgs, 0, newJvmArgs, extraArgs, jvmArgs.length);
            return newJvmArgs;
        } catch (Exception ex) {
            ex.printStackTrace();
            return jvmArgs;
        }
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
