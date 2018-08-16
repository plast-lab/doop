// A wrapper over Soot's ProcessManifest (parsing binary XML manifests).

package org.clyze.doop.soot.android;

import org.clyze.doop.common.android.AndroidManifest;
import org.clyze.doop.common.android.LayoutControl;
import soot.jimple.infoflow.android.axml.AXmlNode;
import soot.jimple.infoflow.android.manifest.ProcessManifest;
import soot.jimple.infoflow.android.resources.ARSCFileParser;
import soot.jimple.infoflow.android.resources.DirectLayoutFileParser;
import soot.jimple.infoflow.android.resources.PossibleLayoutControl;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AndroidManifestAXML implements AndroidManifest {
    private ProcessManifest pm;
    private DirectLayoutFileParser lfp;
    private ARSCFileParser resParser;
    private String apkLocation;

    public AndroidManifestAXML(String apkLocation) throws IOException, org.xmlpull.v1.XmlPullParserException {
        this.apkLocation = apkLocation;
        this.pm = new ProcessManifest(apkLocation);
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

    private void initLayoutFileParser() throws IOException {
        ARSCFileParser resParser = new ARSCFileParser();
        resParser.parse(apkLocation);
        String packageName = getPackageName();
        if (packageName == null)
            throw new RuntimeException("getCallbackMethods(): No package name.");
        lfp = new DirectLayoutFileParser(packageName, resParser);
        lfp.registerLayoutFilesDirect(apkLocation);
        lfp.parseLayoutFileDirect(apkLocation);
    }

    public Set<String> getCallbackMethods() throws IOException {
        if (lfp == null)
            initLayoutFileParser();

        // Collect results.
        Set<String> ret = new HashSet<>();
        for (Set<String> callbackMethods : lfp.getCallbackMethods().values())
            ret.addAll(callbackMethods);
        return ret;
    }

    public Set<LayoutControl> getUserControls() throws IOException {
        if (lfp == null)
            initLayoutFileParser();

        // Collect results.
        Set<LayoutControl> ret = new HashSet<>();
        for (Set<PossibleLayoutControl> possibleLayoutControls : lfp.getUserControls().values()) {
            for (PossibleLayoutControl plc : possibleLayoutControls)
                ret.add(new SootLayoutControl(plc));
        }
        return ret;
    }

    /**
     * Thin wrapper over Soot's PossibleLayoutControl.
     */
    private class SootLayoutControl extends LayoutControl {
        private PossibleLayoutControl plc;
        public SootLayoutControl(PossibleLayoutControl plc) {
            this.plc = plc;
        }
        public int getID() { return plc.getID(); }
        public boolean isSensitive() { return plc.isSensitive(); }
        public String getViewClassName() { return plc.getViewClassName(); }
        public int getParentID() { return plc.getParentID(); }
        public Map<String, Object> getAdditionalAttributes() {
            return plc.getAdditionalAttributes();
        }
    }
}
