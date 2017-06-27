// A wrapper over Soot's ProcessManifest (parsing binary XML manifests).

package org.clyze.doop.soot.android;

import java.io.FileInputStream;
import java.util.HashSet;
import java.util.Set;
import soot.jimple.infoflow.android.axml.AXmlNode;
import soot.jimple.infoflow.android.manifest.ProcessManifest;

public class AndroidManifestAXML implements AndroidManifest {
    private ProcessManifest pm;

    public AndroidManifestAXML(ProcessManifest pm) {
        this.pm = pm;
    }

    public String getApplicationName() { return pm.getApplicationName(); }
    public String getPackageName() { return pm.getPackageName(); }

    public Set<String> getServices() {
        Set<String> r = new HashSet<>();
        for (AXmlNode node : pm.getServices()) {
            r.add(node.getAttribute("name").getValue().toString());
        }
        return r;
    }
    public Set<String> getActivities() {
        Set<String> r = new HashSet<>();
        for (AXmlNode node : pm.getActivities()) {
            r.add(node.getAttribute("name").getValue().toString());
        }
        return r;
    }
    public Set<String> getProviders() {
        Set<String> r = new HashSet<>();
        for (AXmlNode node : pm.getProviders()) {
            r.add(node.getAttribute("name").getValue().toString());
        }
        return r;
    }
    public Set<String> getReceivers() {
        Set<String> r = new HashSet<>();
        for (AXmlNode node : pm.getReceivers()) {
            r.add(node.getAttribute("name").getValue().toString());
        }
        return r;
    }
}
