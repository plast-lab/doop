package org.clyze.doop.common;

import org.apache.commons.logging.Log;

/**
 * A collection of auxiliary methods for logging using apache-commons
 * with a failback to standard error.
 */
public class FrontEndLogger {
    public static void logDebug(Log logger, String s) {
        if (logger == null)
            System.err.println(s);
        else
            logger.debug(s);
    }

    public static void logWarn(Log logger, String s) {
        if (logger == null)
            System.err.println(s);
        else
            logger.warn(s);
    }

    public static void logError(Log logger, String s) {
        if (logger == null)
            System.err.println(s);
        else
            logger.error(s);
    }
}
