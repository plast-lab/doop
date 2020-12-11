package org.clyze.doop.soot;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import org.apache.log4j.Logger;

/*
 * This class provides the conventions that are followed by the Soot
 * front end. These conventions are provided here instead of the fact
 * generator subproject, so that they can be visible to code using
 * Doop as a library.
 */
public class DoopConventions {

    private static final Logger logger = Logger.getLogger(DoopConventions.class);

    public static String jimpleDir(String outDir) {
        return outDir + File.separatorChar + "jimple";
    }

    private static final String LOCAL_SEPARATOR = "_$$A_";

    public static boolean setSeparatorFailed = false;

    /**
     * Call setSeparator() on Soot to set the fresh variable separator
     * (needed to discover the original names of SSA-transformed locals).
     */
    public static void setSeparator() {
        try {
            Method setter = Class.forName("soot.shimple.internal.ShimpleBodyBuilder").getDeclaredMethod("setSeparator", String.class);
            setter.setAccessible(true);
            setter.invoke(null, LOCAL_SEPARATOR);
            logger.debug("Using separator for fresh variables in Soot: " + LOCAL_SEPARATOR);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
            // ex.printStackTrace();
            logger.debug("Using default fresh variable separator in Soot.");
            setSeparatorFailed = true;
        }
    }

    /**
     * Clients of Doop can read the separator to be able to reason
     * about local names in generated Jimple.
     */
    @SuppressWarnings("unused")
    public static String getSeparator() {
        return LOCAL_SEPARATOR;
    }

}
