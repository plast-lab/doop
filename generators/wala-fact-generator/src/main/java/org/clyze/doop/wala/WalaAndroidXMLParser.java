package org.clyze.doop.wala;

import java.util.*;
import org.apache.log4j.Logger;
import org.clyze.doop.common.BasicJavaSupport;
import org.clyze.doop.common.Parameters;
import org.clyze.doop.common.android.AppResources;
import org.clyze.doop.common.android.AndroidSupport;

/*
 * Parses all the XML files of each input file to find all the information we want about
 * Android Components and LayoutControls
 * WARNING: It uses the soot implementation, need to find alternative
 */
class WalaAndroidXMLParser extends AndroidSupport {
    private final WalaFactWriter factWriter;
    private final Logger logger = Logger.getLogger(getClass());

    WalaAndroidXMLParser(Parameters parameters, WalaFactWriter writer, BasicJavaSupport java)
    {
        super(parameters, java);
        this.factWriter = writer;
    }

    void process()
    {
        parseXMLFiles();
        populateArtifactsRelation();
        writeComponents(factWriter);
    }

    private void parseXMLFiles()
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
                    resources.printManifestInfo();
                } catch (Exception ex) {
                    logger.debug("Error processing manifest in: " + i);
                    ex.printStackTrace();
                }
            }
        }
    }

    private void populateArtifactsRelation() {
        for (String i : parameters.getInputs())
            if (i.endsWith(".apk"))
                java.getArtifactScanner().processAPKClasses(i, null);
    }
}
