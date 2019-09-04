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
    public static void invokeResourceJar(Logger log, String resource, String TAG, String[] jvmArgs, String[] args)
        throws IOException {
        String resourceJar = null;
        String doopHome = System.getenv("DOOP_HOME");

        List<String> matches = new LinkedList<>();
        if (doopHome != null) {
            for (File f : (new File(doopHome + File.separator + "resources")).listFiles())
                if (f.getName().startsWith(resource))
                    matches.add(f.getAbsolutePath());

            if ((matches != null) && matches.size() > 0) {
                // Use last JAR in case many are found (to select most recent version).
                Collections.sort(matches);
                resourceJar = matches.get(matches.size()-1);
                log.debug("Running resource: " + resourceJar);
            }
        }

        if (resourceJar == null) {
            String msg = "Bundled resource could not be found: " + resource + " (doopHome: " + doopHome + ", matching resources: " + matches.size() + ")";
            System.err.println("ERROR: " + msg);
            throw new RuntimeException(msg);
        }

        if (jvmArgs == null)
            jvmArgs = new String[0];

        OutputConsumer proc = new OutputConsumer();
        JHelper.runJar(new String[0], jvmArgs, resourceJar, args, TAG, log.isDebugEnabled(), proc);
        if (proc.error != null)
            throw new RuntimeException(proc.error);
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
