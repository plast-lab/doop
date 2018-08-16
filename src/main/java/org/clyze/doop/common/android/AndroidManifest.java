// An Android manifest, with the information needed by Doop.

package org.clyze.doop.common.android;

import java.io.IOException;
import java.util.Set;
import org.clyze.doop.soot.android.AndroidManifestAXML;

public interface AndroidManifest {
    String getApplicationName();
    String getPackageName();
    Set<String> getServices();
    Set<String> getActivities();
    Set<String> getProviders();
    Set<String> getReceivers();
    Set<String> getCallbackMethods() throws IOException;
    Set<LayoutControl> getUserControls() throws IOException;

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

    // Parses Android manifests. Supports binary and plain-text XML
    // files (found in .apk and .aar files respectively).
    static AndroidManifest getAndroidManifest(String archiveLocation) throws Exception {
        try {
            return new AndroidManifestAXML(archiveLocation);
        } catch (Exception ex) {
            return AndroidManifestXML.fromArchive(archiveLocation);
        }
    }

}
