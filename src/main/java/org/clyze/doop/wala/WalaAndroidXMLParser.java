package org.clyze.doop.wala;

import org.clyze.doop.common.BasicJavaSupport;
import org.clyze.doop.common.android.AndroidManifest;
import org.clyze.doop.common.android.AndroidSupport;
import org.clyze.doop.common.android.LayoutControl;

import java.io.IOException;
import java.util.*;

import static org.clyze.doop.soot.android.AndroidSupport_Soot.newAndroidManifest;

/*
 * Parses all the XML files of each input file to find all the information we want about
 * Android Components and LayoutControls
 * WARNING: It uses the soot implementation, need to find alternative
 */
public class WalaAndroidXMLParser extends AndroidSupport {
    private WalaParameters walaParameters;
    private WalaFactWriter factWriter;

    WalaAndroidXMLParser(WalaParameters walaParameters, WalaFactWriter writer, BasicJavaSupport java)
    {
        super(null, null, java);
        this.walaParameters = walaParameters;
        this.factWriter = writer;
    }

    void parseXMLFiles()
    {
        Map<String, String> pkgs = new HashMap<>();

        // We merge the information from all manifests, not just
        // the application's. There are Android apps that use
        // components (e.g. activities) from AAR libraries.
        for (String i : walaParameters.getInputs()) {
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
        for (String appInput : walaParameters.getInputs()) {
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

            Set<LayoutControl> layoutControls = null;
            try {
                layoutControls = processMan.getUserControls();
            } catch (IOException e) {
                System.err.println("Error while reading layout controls:");
                e.printStackTrace();
            }
            if(layoutControls != null) {
                for (LayoutControl possibleLayoutControl : layoutControls) {
                    factWriter.writeLayoutControl(possibleLayoutControl.getID(), possibleLayoutControl.getViewClassName(), possibleLayoutControl.getParentID());
                    if (possibleLayoutControl.isSensitive()) {
                        factWriter.writeSensitiveLayoutControl(possibleLayoutControl.getID(), possibleLayoutControl.getViewClassName(), possibleLayoutControl.getParentID());
                    }
                }
            }
            factWriter.writeExtraSensitiveControls(walaParameters);
        }
    }

    @Override
    public AndroidManifest getAndroidManifest(String archiveLocation) throws Exception {
        return newAndroidManifest(archiveLocation);
    }
}
