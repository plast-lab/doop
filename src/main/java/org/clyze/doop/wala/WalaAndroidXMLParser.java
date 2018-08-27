package org.clyze.doop.wala;

import org.clyze.doop.common.BasicJavaSupport;
import org.clyze.doop.common.android.AppResources;
import org.clyze.doop.common.android.AndroidSupport;

import java.util.*;

/*
 * Parses all the XML files of each input file to find all the information we want about
 * Android Components and LayoutControls
 * WARNING: It uses the soot implementation, need to find alternative
 */
public class WalaAndroidXMLParser extends AndroidSupport {
    private WalaFactWriter factWriter;

    WalaAndroidXMLParser(WalaParameters parameters, WalaFactWriter writer, BasicJavaSupport java)
    {
        super(parameters, java);
        this.factWriter = writer;
    }

    void parseXMLFiles()
    {
        Map<String, String> pkgs = new HashMap<>();

        // We merge the information from all manifests, not just
        // the application's. There are Android apps that use
        // components (e.g. activities) from AAR libraries.
        for (String i : parameters.getInputs()) {
            if (i.endsWith(".apk") || i.endsWith(".aar")) {
                System.out.println("Processing resources in " + i);
                try {
                    AppResources resources = processAppResources(i);
                    processAppResources(i, resources, pkgs, null);
                    resources.printManifestHeader();
                } catch (Exception ex) {
                    System.err.println("Error processing manifest in: " + i);
                    ex.printStackTrace();
                }
            }
        }
    }

    public void writeComponents() {
        super.writeComponents(factWriter, parameters);
    }
}
