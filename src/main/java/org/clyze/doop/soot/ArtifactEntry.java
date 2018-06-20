package org.clyze.doop.soot;

import java.util.HashSet;
import java.util.Set;

public class ArtifactEntry {
    // Class name inside an artifact.
    String className;
    // Sub artifact (such as "classes.dex" in an APK or "classes.jar" in an AAR).
    String subArtifact;

    public ArtifactEntry(String className, String subArtifact) {
        this.className = className;
        this.subArtifact = subArtifact;
    }

    public static Set<String> toClassNames(Set<ArtifactEntry> s) {
        Set<String> ret = new HashSet<>();
        for (ArtifactEntry ae : s)
            ret.add(ae.className);
        return ret;
    }
}
