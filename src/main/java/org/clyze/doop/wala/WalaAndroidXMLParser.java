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

    WalaAndroidXMLParser(WalaParameters parameters, WalaFactWriter writer, BasicJavaSupport java)
    {
        super(null, null, java);
        this.walaParameters = parameters;
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
        super.writeComponents(factWriter, walaParameters);
    }

    @Override
    public AndroidManifest getAndroidManifest(String archiveLocation) throws Exception {
        return newAndroidManifest(archiveLocation);
    }
}
