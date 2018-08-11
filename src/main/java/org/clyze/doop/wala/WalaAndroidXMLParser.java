package org.clyze.doop.wala;

import org.clyze.doop.common.ArtifactEntry;
import org.clyze.doop.soot.PropertyProvider;
import org.clyze.doop.soot.android.AndroidManifest;
import org.clyze.doop.soot.android.AndroidSupport;
import soot.jimple.infoflow.android.resources.PossibleLayoutControl;

import java.io.IOException;
import java.util.*;

import static org.clyze.doop.soot.android.AndroidManifest.getAndroidManifest;

/*
 * Parses all the XML files of each input file to find all the information we want about
 * Android Components and LayoutControls
 * WARNING: It uses the soot implementation, need to find alternative
 */
public class WalaAndroidXMLParser extends AndroidSupport {
    private List<String> inputs;
    private WalaFactWriter factWriter;
    private String extraSensitiveControls;

    WalaAndroidXMLParser(Map<String, Set<ArtifactEntry>> artifactToClassMap, PropertyProvider propertyProvider, List<String> inputFiles, WalaFactWriter writer, String _extraSensitiveControls)
    {
        super(artifactToClassMap, propertyProvider, null, null);
        inputs = inputFiles;
        factWriter = writer;
        extraSensitiveControls = _extraSensitiveControls;
    }

    void parseXMLFiles()
    {
        Map<String, String> pkgs = new HashMap<>();

        // We merge the information from all manifests, not just
        // the application's. There are Android apps that use
        // components (e.g. activities) from AAR libraries.
        for (String i : inputs) {
            if (i.endsWith(".apk") || i.endsWith(".aar")) {
                System.out.println("Processing manifest in " + i);
                try {
                    AndroidManifest manifest = getAndroidManifest(i);
                    processManifest(i, manifest, pkgs, null);
                    manifest.printManifestHeader();
                } catch (Exception ex) {
                    System.err.println("Error processing manifest in: " + i);
                    ex.printStackTrace();
                    continue;
                }
            }
        }
    }

    public void writeComponents() {
        for (String appInput : inputs) {
            AndroidManifest processMan;
            try {
                processMan = getAndroidManifest(appInput);
            } catch (Exception ex) {
                System.err.println("Error processing manifest in: " + appInput);
                ex.printStackTrace();
                continue;
            }

            if (processMan.getApplicationName() != null)
                factWriter.writeApplication(processMan.expandClassName(processMan.getApplicationName()));
            else {
                // If no application name, use Android's Application:
                // "The fully qualified name of an Application subclass
                // implemented for the application. ... In the absence of a
                // subclass, Android uses an instance of the base
                // Application class."
                // https://developer.android.com/guide/topics/manifest/application-element.html
                factWriter.writeApplication("android.app.Application");
            }

            for (String s : processMan.getActivities()) {
                factWriter.writeActivity(processMan.expandClassName(s));
            }

            for (String s : processMan.getServices()) {
                factWriter.writeService(processMan.expandClassName(s));
            }

            for (String s : processMan.getProviders()) {
                factWriter.writeContentProvider(processMan.expandClassName(s));
            }

            for (String s : processMan.getReceivers()) {
                factWriter.writeBroadcastReceiver(processMan.expandClassName(s));
            }

            Set<String> callBackMethods = null;
            try {
                callBackMethods = processMan.getCallbackMethods();
            } catch (IOException ex) {
                System.err.println("Error while reading callbacks:");
                ex.printStackTrace();
            }
            if(callBackMethods != null) {
                for (String callbackMethod : callBackMethods) {
                    factWriter.writeCallbackMethod(callbackMethod);
                }
            }

            Set<PossibleLayoutControl> layoutControls = null;
            try {
                layoutControls = processMan.getUserControls();
            } catch (IOException e) {
                System.err.println("Error while reading layout controls:");
                e.printStackTrace();
            }
            if(layoutControls != null) {
                for (PossibleLayoutControl possibleLayoutControl : layoutControls) {
                    factWriter.writeLayoutControl(possibleLayoutControl.getID(), possibleLayoutControl.getViewClassName(), possibleLayoutControl.getParentID());
                    if (possibleLayoutControl.isSensitive()) {
                        factWriter.writeSensitiveLayoutControl(possibleLayoutControl.getID(), possibleLayoutControl.getViewClassName(), possibleLayoutControl.getParentID());
                    }
                }
            }
            writeExtraSensitiveControls();
        }
    }

    // The extra sensitive controls are given as a String
    // "id1,type1,parentId1,id2,type2,parentId2,...".
    private void writeExtraSensitiveControls() {
        if (extraSensitiveControls.equals("")) {
            return;
        }
        String[] parts = extraSensitiveControls.split(",");
        int partsLen = parts.length;
        if (partsLen % 3 != 0) {
            System.err.println("List size (" + partsLen + ") not a multiple of 3: \"" + extraSensitiveControls + "\"");
            return;
        }
        for (int i = 0; i < partsLen; i += 3) {
            String control = parts[i] + "," + parts[i+1] + "," + parts[i+2];
            try {
                int controlId = Integer.parseInt(parts[i]);
                String typeId = parts[i+1].trim();
                int parentId  = Integer.parseInt(parts[i+2]);
                System.out.println("Adding sensitive layout control: " + control);
                factWriter.writeSensitiveLayoutControl(controlId, typeId, parentId);
            } catch (Exception ex) {
                System.err.println("Ignoring control: " + control);
            }
        }
    }
}
