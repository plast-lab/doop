package org.clyze.doop.utils

import groovy.transform.TypeChecked
import org.apache.log4j.Logger
import org.clyze.doop.common.DoopErrorCodeException
import org.clyze.doop.core.Doop
import org.clyze.utils.JHelper

/**
 * A class that provides functionality to find and execute bundled resources
 * (such as standalone programs in JAR form).
 */
@TypeChecked
public class Resources {
    public static void invokeResourceJar(Logger log, String frontEnd, String TAG, String[] jvmArgs, String[] args) {
        String frontEndJar = null

        if (Doop.doopHome) {
            List<String> jars = []
            (new File("${Doop.doopHome}/resources/")).eachFile {
                if (it.name.startsWith(frontEnd)) {
                    jars.add(it.canonicalPath)
                }
            }
            if (jars && jars.size() > 0) {
                // Use last JAR in case many are found (to select most recent version).
                frontEndJar = jars.sort().get(jars.size()-1)
                log.debug "Using front end: ${frontEndJar}"
            }
        }

        if (frontEndJar == null) {
            String msg = "Front end could not be found: " + frontEnd
            System.err.println("ERROR: ${msg}");
            throw new RuntimeException(msg)
        }

        jvmArgs = jvmArgs ?: new String[0]

        String error = null
        def proc = { String line -> if (line.contains(DoopErrorCodeException.PREFIX)) error = line }
        JHelper.runJar(new String[0], jvmArgs, frontEndJar, args, TAG, log.debugEnabled, proc);
        if (error)
            throw new RuntimeException(error)
    }
}
