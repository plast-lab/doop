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

import static org.clyze.doop.soot.android.AndroidManifest.*;

public class AndroidManifestXML implements AndroidManifest {
    private String applicationName, packageName;
    private Set<String> activities = new HashSet<>();
    private Set<String> providers  = new HashSet<>();
    private Set<String> receivers  = new HashSet<>();
    private Set<String> services   = new HashSet<>();

    public AndroidManifestXML(String archiveLocation) {
        Document doc = null;
        try {
            InputStream is = getInputStream(new File(archiveLocation), "AndroidManifest.xml");
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
        printManifestInfo();
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

    static InputStream getInputStream(File zip, String entry) throws IOException {
        ZipInputStream zin = new ZipInputStream(new FileInputStream(zip));
        for (ZipEntry e; (e = zin.getNextEntry()) != null;) {
            if (e.getName().equals(entry)) {
                return zin;
            }
        }
        throw new RuntimeException("Cannot find " + entry);
    }
}
