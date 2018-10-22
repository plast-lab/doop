package org.clyze.doop.wala;

import java.io.File;
import java.util.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.clyze.doop.common.BasicJavaSupport;
import org.clyze.doop.common.Parameters;
import org.clyze.doop.common.android.AppResources;
import org.clyze.doop.common.android.AndroidSupport;
import org.clyze.doop.util.TypeUtils;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.iface.MultiDexContainer;
import org.jf.dexlib2.dexbacked.DexBackedClassDef;

import static org.jf.dexlib2.DexFileFactory.loadDexContainer;

/*
 * Parses all the XML files of each input file to find all the information we want about
 * Android Components and LayoutControls
 * WARNING: It uses the soot implementation, need to find alternative
 */
class WalaAndroidXMLParser extends AndroidSupport {
    private final WalaFactWriter factWriter;
    private final Log logger;

    WalaAndroidXMLParser(Parameters parameters, WalaFactWriter writer, BasicJavaSupport java)
    {
        super(parameters, java);
        this.factWriter = writer;
        this.logger = LogFactory.getLog(getClass());
    }

    void process()
    {
        parseXMLFiles();
        populateArtifactsRelation();
        writeComponents(factWriter, parameters);
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
                try {
                    Opcodes opcodes = Opcodes.getDefault();
                    File apk = new File(i);
                    MultiDexContainer<? extends DexBackedDexFile> multiDex = loadDexContainer(apk, opcodes);
                    for (String dexEntry : multiDex.getDexEntryNames()) {
                        DexBackedDexFile dex = multiDex.getEntry(dexEntry);
                        if (dex == null)
                            logger.debug("No .dex entry for " + dexEntry);
                        else
                            for (DexBackedClassDef dexClass : dex.getClasses()) {
                                String className = TypeUtils.raiseTypeId(dexClass.getType());
                                java.registerArtifactClass(apk.getName(), className, dexEntry);
                            }
                    }
                } catch (Exception ex) {
                    logger.debug("Error while calculating artifacts on Android: " + ex.getMessage());
                }
    }
}
