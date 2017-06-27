// An Android manifest, with the information needed by Doop.

package org.clyze.doop.soot.android;

import java.util.Set;
import soot.jimple.infoflow.android.manifest.ProcessManifest;

public interface AndroidManifest {
    String getApplicationName();
    String getPackageName();
    Set<String> getServices();
    Set<String> getActivities();
    Set<String> getProviders();
    Set<String> getReceivers();

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
        System.out.println("application name: " + getApplicationName());
        System.out.println("package name: " + getPackageName());
        System.out.println("activities: " + getActivities());
        System.out.println("content providers: " + getProviders());
        System.out.println("broadcast receivers: " + getReceivers());
        System.out.println("services: " + getServices());
    }

    // Parses Android manifests. Supports binary and plain-text XML
    // files (found in .apk and .aar files respectively).
    static AndroidManifest getAndroidManifest(String archiveLocation) {
        try {
            return new AndroidManifestAXML(new ProcessManifest(archiveLocation));
        } catch (Exception ex) {
            return new AndroidManifestXML(archiveLocation);
        }
    }

}
