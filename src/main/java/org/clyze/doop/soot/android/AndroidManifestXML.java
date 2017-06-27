package org.clyze.doop.soot.android;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

import soot.jimple.infoflow.android.resources.PossibleLayoutControl;

import static org.clyze.doop.soot.android.AndroidManifest.*;

public class AndroidManifestXML implements AndroidManifest {
    private File archive;
    private String applicationName, packageName;
    private Set<String> activities = new HashSet<>();
    private Set<String> providers  = new HashSet<>();
    private Set<String> receivers  = new HashSet<>();
    private Set<String> services   = new HashSet<>();

    public AndroidManifestXML(String archiveLocation) {
        Document doc = null;
        try {
            this.archive = new File(archiveLocation);
            InputStream is = getZipEntryInputStream("AndroidManifest.xml");
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            doc = dbf.newDocumentBuilder().parse(is);
        } catch (Exception ex) {
            ex.printStackTrace();
            return;
        }

        Element docElem = doc.getDocumentElement();
        if (!docElem.getNodeName().equals("manifest"))
            throw new RuntimeException("No <manifest> root: " + archiveLocation);

        // Initialize 'packageName' field.
        NamedNodeMap rootAttrs = docElem.getAttributes();
        if (rootAttrs != null) {
            Node n = rootAttrs.getNamedItem("package");
            if (n != null) {
                packageName = n.getNodeValue();
            }
        }

        // Initialize 'applicationName' field.
        NodeList l0 = docElem.getChildNodes();
        for (int i0 = 0; i0 < l0.getLength(); i0++) {
            Node appNode = l0.item(i0);
            if (appNode.getNodeName().equals("application")) {
                NamedNodeMap appAttrs = appNode.getAttributes();
                if (appAttrs != null) {
                    Node appNodeName = appAttrs.getNamedItem("android:name");
                    if (appNodeName != null) {
                        applicationName = appNodeName.getNodeValue();
                        System.out.println("Application found: " + applicationName);
                    }
                }
                // Initialize activities/providers/receivers/services.
                NodeList l1 = appNode.getChildNodes();
                for (int i1 = 0; i1 < l1.getLength(); i1++) {
                    Node appElem = l1.item(i1);
                    registerAppNode(appElem, appElem.getAttributes());
                }
            }
        }
    }

    private void registerAppNode(Node appElem, NamedNodeMap attrs) {
        if (attrs == null)
            return;
        Node n = attrs.getNamedItem("android:name");
        if (n == null)
            return;
        if (appElem.getNodeName().equals("activity")) {
            activities.add(n.getNodeValue());
        } else if (appElem.getNodeName().equals("provider")) {
            providers.add(n.getNodeValue());
        } else if (appElem.getNodeName().equals("receiver")) {
            receivers.add(n.getNodeValue());
        } else if (appElem.getNodeName().equals("services")) {
            services.add(n.getNodeValue());
        }
    }

    public String getApplicationName() { return applicationName; }
    public String getPackageName()     { return packageName;     }
    public Set<String> getServices()   { return services;        }
    public Set<String> getActivities() { return activities;      }
    public Set<String> getProviders()  { return providers;       }
    public Set<String> getReceivers()  { return receivers;       }

    private InputStream getZipEntryInputStream(String entry) throws IOException {
        ZipInputStream zin = new ZipInputStream(new FileInputStream(archive));
        for (ZipEntry e; (e = zin.getNextEntry()) != null;) {
            if (e.getName().equals(entry)) {
                return zin;
            }
        }
        throw new RuntimeException("Cannot find " + entry);
    }

    private void findOnClickHandlers(Node e, Set<String> accumulator) {
        NamedNodeMap attrs = e.getAttributes();
        if (attrs != null) {
            Node n = attrs.getNamedItem("android:onClick");
            if (n != null)
                accumulator.add(n.getNodeValue());
        }
        NodeList children = e.getChildNodes();
        for (int i = 0; i < children.getLength(); i++)
            findOnClickHandlers(children.item(i), accumulator);
    }

    public Set<String> getCallbackMethods() throws IOException {
        // We assume all callback methods are defined as
        // 'android:onClick' attributes in XML files under /res.

        Set<String> ret = new HashSet<>();
        ZipInputStream zin = null;
        try {
            zin = new ZipInputStream(new FileInputStream(archive));
        } catch (Exception ex) {
            ex.printStackTrace();
            return ret;
        }

        // Find all xml files under /res in the archive.
        Set<String> resXMLs = new HashSet<>();
        for (ZipEntry e; (e = zin.getNextEntry()) != null;) {
            String name = e.getName();
            if (name.startsWith("res/") &&
                (name.endsWith(".xml") || name.endsWith(".XML")))
                resXMLs.add(name);
        }

        // Parse each XML to find possible callbacks.
        try {
            for (String resXML : resXMLs) {
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                InputStream is = getZipEntryInputStream(resXML);
                Document doc = dbf.newDocumentBuilder().parse(is);
                findOnClickHandlers(doc.getDocumentElement(), ret);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return ret;
    }

    public Set<PossibleLayoutControl> getUserControls() {
        System.out.println("WARNING: getUserControls() not yet implemented for plain-text XML files.");
        return new HashSet<>();
    }

}
