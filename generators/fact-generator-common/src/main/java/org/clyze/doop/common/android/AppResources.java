// An Android manifest, with the information needed by Doop.

package org.clyze.doop.common.android;

import java.io.IOException;
import java.util.Set;

/**
 * The non-code information that Doop needs from an Android app,
 * e.g., resources found in AndroidManifest.xml or other .xml files.
 */
public interface AppResources {
    String getApplicationName();
    String getPackageName();
    Set<String> getServices();
    Set<String> getActivities();
    Set<String> getProviders();
    Set<String> getReceivers();
    Set<String> getCallbackMethods() throws IOException;
    Set<LayoutControl> getUserControls();

    // Adapted from Soot's ProcessManifest.
    default String expandClassName(String className) {
        String packageName = getPackageName();
        boolean dotFirst = className.startsWith(".");
        boolean dotsExist = className.contains(".");

        // Sanity check.
        if ((packageName == null) && (dotFirst || (!dotsExist)))
            throw new RuntimeException("No package prefix for " + className);

        if (dotFirst)
            return packageName + className;
        else if (!dotsExist)
            return packageName + "." + className;
        else
            return className;
    }

    default void printManifestInfo() {
        printManifestHeader();
        System.out.println("activities: " + getActivities());
        System.out.println("content providers: " + getProviders());
        System.out.println("broadcast receivers: " + getReceivers());
        System.out.println("services: " + getServices());
        try {
            System.out.println("callbacks: " + getCallbackMethods());
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    default void printManifestHeader() {
        System.out.println("application name: " + getApplicationName() +
                           " (package name: " + getPackageName() + ")");
    }

}
