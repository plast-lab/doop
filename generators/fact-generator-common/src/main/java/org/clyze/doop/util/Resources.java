package org.clyze.doop.util;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import org.apache.log4j.Logger;
import org.clyze.doop.common.DoopErrorCodeException;
import org.clyze.utils.JHelper;

/**
 * A class that provides functionality to find and execute bundled resources
 * (such as standalone programs in JAR form).
 */
public class Resources {

    /**
     * Run a program bundled as a JAR in the resources.
     *
     * @param doopHome     the Doop installation home
     * @param logger       the log4j logger to use
     * @param TAG          the tag to use to mark program output
     * @param jvmArgs      the JVM arguments (may be null)
     * @param resource     the prefix of the resource JAR (should match one resource)
     * @param args         the arguments to pass to the program
     */
    public static void invokeResourceJar(String doopHome, Logger logger, String TAG, String[] jvmArgs, String resource, String[] args)
        throws IOException {
        String resourceJar = null;

        List<String> matches = new LinkedList<>();
        if (doopHome != null) {
            // Remove quotes used for escaping in the command line.
            doopHome = doopHome.replaceAll("\"", "");
            File resourcesDir = new File(doopHome + File.separator + "resources");
            if (!resourcesDir.exists())
                throw new RuntimeException("ERROR: resources directory does not exist: " + resourcesDir.getCanonicalPath());
            for (File f : resourcesDir.listFiles())
                if (f.getName().startsWith(resource))
                    matches.add(f.getCanonicalPath());

            if ((matches != null) && matches.size() > 0) {
                // Use last JAR in case many are found (to select most recent version).
                Collections.sort(matches);
                resourceJar = matches.get(matches.size()-1);
                logger.debug("Running resource: " + resourceJar);
            }
        } else
            throw new RuntimeException("ERROR: cannot find resource '" + resource + "', no DOOP_HOME");

        if (resourceJar == null) {
            String msg = "Bundled resource could not be found: " + resource + " (doopHome: " + doopHome + ", matching resources: " + matches.size() + ")";
            System.err.println("ERROR: " + msg);
            throw new RuntimeException(msg);
        }

        // Add extra flags.
        jvmArgs = extraJvmArgs(jvmArgs, doopHome);

        OutputConsumer proc = new OutputConsumer();
        JHelper.runJar(new String[0], jvmArgs, resourceJar, args, TAG, logger.isDebugEnabled(), proc);
        if (proc.error != null)
            throw new RuntimeException(proc.error);
    }

    private static String[] extraJvmArgs(String[] jvmArgs, String doopHome) {
        if (jvmArgs == null)
            jvmArgs = new String[0];

        int extraArgs = 1;
        String[] newJvmArgs = new String[jvmArgs.length + extraArgs];
        // Pass DOOP_HOME to called program via system property.
        newJvmArgs[0] = "-DDOOP_HOME=\"" + doopHome + "\"";
        System.arraycopy(jvmArgs, 0, newJvmArgs, extraArgs, jvmArgs.length);
        return newJvmArgs;
    }

    /**
     * Method to find DOOP_HOME when the Doop class is not available.
     */
    public static String findDoopHome(Logger logger) {
        String doopHome = System.getenv("DOOP_HOME");
        if (doopHome == null)
            doopHome = System.getProperty("DOOP_HOME");
        logger.debug("findDoopHome()=" + doopHome);
        return doopHome;
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
