package org.clyze.doop.common.android;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import net.dongliu.apk.parser.ApkFile;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

/**
 * This is the standard implementation of AppResources.
 * It parses both plaintext and binary .xml files found in
 * an input archive (usually .aar or .apk respectively).
 */
public class AppResourcesXML implements AppResources {
    private static final String MANIFEST = "AndroidManifest.xml";
    private final File archive;
    private final boolean isApk;
    private String applicationName, packageName;
    private final Set<String> activities = new HashSet<>();
    private final Set<String> providers  = new HashSet<>();
    private final Set<String> receivers  = new HashSet<>();
    private final Set<String> services   = new HashSet<>();
    private static final boolean verbose = false;
    private static final Map<String, String> replaceMap = new ConcurrentHashMap<>();
    private int failures = 0;

    public static AppResourcesXML fromAAR(String archiveLocation)
            throws IOException, ParserConfigurationException, SAXException {
        File ar = new File(archiveLocation);
        return new AppResourcesXML(ar, false);
    }

    public static AppResourcesXML fromAPK(String apkLocation)
            throws IOException, ParserConfigurationException, SAXException {
        return new AppResourcesXML(new File(apkLocation), true);
    }

    private AppResourcesXML(File ar, boolean isApk)
            throws IOException, ParserConfigurationException, SAXException {
        this.archive = ar;
        this.isApk = isApk;

        InputStream is = isApk ? getApkManifest() : getZipEntryInputStream(MANIFEST);

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        Document doc = dbf.newDocumentBuilder().parse(is);

        Element docElem = doc.getDocumentElement();
        if (!docElem.getNodeName().equals("manifest"))
            throw new RuntimeException("No <manifest> root: " + archive);

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
        switch (appElem.getNodeName()) {
            case "activity":
                activities.add(n.getNodeValue());
                break;
            case "provider":
                providers.add(n.getNodeValue());
                break;
            case "receiver":
                receivers.add(n.getNodeValue());
                break;
            case "service":
                services.add(n.getNodeValue());
                break;
        }
    }

    public String getApplicationName() { return applicationName; }
    public String getPackageName()     { return packageName;     }
    public Set<String> getServices()   { return services;        }
    public Set<String> getActivities() { return activities;      }
    public Set<String> getProviders()  { return providers;       }
    public Set<String> getReceivers()  { return receivers;       }

    @SuppressWarnings("CatchMayIgnoreException")
    private InputStream getLayout(String entry) {
        try {
            return getXML(entry);
        } catch (Exception ex) {
            final String[] altLayouts = { "v11", "v16", "v17", "v21", "v22" };
            for (String v : altLayouts ) {
                String l = entry.replaceAll("res/layout/", "res/layout-"+v+"/");
                try {
                    return getXML(l);
                } catch (Exception ex0) { }
            }
        }
        throw new RuntimeException("Cannot find layout " + entry);
    }

    private InputStream getXML(String entry) throws IOException {
        if (isApk)
            return getBinaryXML(entry);
        else
            return getZipEntryInputStream(entry);
    }

    private InputStream getApkManifest() throws IOException {
        try (ApkFile apkFile = new ApkFile(archive)) {
            apkFile.setPreferredLocale(Locale.getDefault());
            String manifestXml = apkFile.getManifestXml();
            return new ByteArrayInputStream(manifestXml.getBytes());
        }
    }

    private static void registerReplacement(String a, String b) {
        replaceMap.put(" " + a + ":", " " + b + ":");
    }

    // This initializes the namespace-prefix replacement map. This replacement fixes
    // parsing of the XML given to us by apk-parser (which expands namespace URLs).
    static {
        registerReplacement("http://schemas.android.com/apk/res-auto", "res-auto");
        registerReplacement("http://schemas.android.com/apk/res/android", "android");
    }

    private InputStream getBinaryXML(String entry) throws IOException {
        try (ApkFile apkFile = new ApkFile(archive)) {
            String xml = apkFile.transBinaryXml(entry);
            // Handle expanded Android namespaces
            for (Map.Entry<String, String> e : replaceMap.entrySet())
                xml = xml.replace(e.getKey(), e.getValue());
            if (verbose)
                System.out.println("xml [" + entry + "]: " + xml);
            return new ByteArrayInputStream(xml.getBytes());
        }
    }

    private InputStream getZipEntryInputStream(String entry) throws IOException {
        ZipInputStream zin = new ZipInputStream(new FileInputStream(archive));
        for (ZipEntry e; (e = zin.getNextEntry()) != null;) {
            if (e.getName().equals(entry) || e.getName().equals(entry + ".xml"))
                return zin;
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
        // Find all xml files under /res in the archive.
        Collection<String> resXMLs = new HashSet<>();
        try (ZipInputStream zin = new ZipInputStream(new FileInputStream(archive))) {
            for (ZipEntry e; (e = zin.getNextEntry()) != null; ) {
                String name = e.getName();
                if (name.startsWith("res/") &&
                        (name.endsWith(".xml") || name.endsWith(".XML")))
                    resXMLs.add(name);
            }
        } catch (Exception ex) {
            handleException(ex);
            return ret;
        }

        // Parse each XML to find possible callbacks.
        for (String resXML : resXMLs) {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            InputStream is = getXML(resXML);
            try {
                Document doc = dbf.newDocumentBuilder().parse(is);
                findOnClickHandlers(doc.getDocumentElement(), ret);
            } catch (SAXException | ParserConfigurationException ex) {
                handleException(ex);
            }
        }
        return ret;
    }

    public Set<LayoutControl> getUserControls() {
        Set<String> layoutFiles = new HashSet<>();
        Set<LayoutControl> controls = new HashSet<>();
        try (ZipInputStream zin = new ZipInputStream(new FileInputStream(archive))) {
            for (ZipEntry e; (e = zin.getNextEntry()) != null;) {
                String name = e.getName();
                if (name.startsWith("res/layout") && name.endsWith(".xml"))
                    layoutFiles.add(name);
            }
            for (String layoutFile : layoutFiles)
                try {
                    getUserControlsForLayoutFile(layoutFile, -1, controls);
                } catch (Exception ex) {
                    handleException(ex);
                }
            if (verbose)
                System.out.println("layoutFiles size: " + layoutFiles.size() + ", failures = " + failures);
        } catch (IOException ex) {
            System.err.println("Error while reading user controls in : " + archive);
            handleException(ex);
        }
        return controls;
    }

    private void getUserControlsForLayoutFile(String layoutFile, int parentId,
                                              Set<LayoutControl> controls) throws Exception {
        if (verbose)
            System.out.println("Processing user controls in layout file: " + layoutFile);
        InputStream is = getLayout(layoutFile);
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        Document doc = dbf.newDocumentBuilder().parse(is);
        Element docElem = doc.getDocumentElement();
        NodeList l0 = docElem.getChildNodes();
        for (int i0 = 0; i0 < l0.getLength(); i0++) {
            getUserControlsForNode(l0.item(i0), parentId, controls);
        }
    }

    // Layout controls are triplets (id, control name, parent id) and
    // can be sensitive.
    private void getUserControlsForNode(Node node, int parentId,
                                        Set<LayoutControl> controls) throws Exception {
        String name = node.getNodeName();
        if (verbose)
            System.out.println("Processing user controls in node: " + name);
        if (name.equals("dummy") || name.equals("#comment") || name.equals("#text")) {
            return;
        } else if (name.equals("include")) {
            String includedLayout = attrOrDefault(node, "layout", null);
            if (includedLayout != null) {
                if (includedLayout.startsWith("@layout/")) {
                    String layoutFile = "res/" + includedLayout.substring(1);
                    getUserControlsForLayoutFile(layoutFile, parentId, controls);
                } else if (includedLayout.startsWith("res/layout/")) {
                    getUserControlsForLayoutFile(includedLayout, parentId, controls);
                } else {
                    System.err.println("unsupported include: " + includedLayout);
                }
            }
            return;
        }

        // Default control id, if no matching R entry is found.
        int intId = -1;
        // 'Merge' elements don't represent controls, skip to process children.
        if (!name.equals("merge")) {
            if (name.equals("fragment"))
                name = attrOrDefault(node, "android:name", "-1");
            String id = attrOrDefault(node, "android:id", "-1");
            // System.out.println("name = " + name + ", id = " + id + ", parentId = " + parentId);
            final String RESOURCE_ID = "resourceId:";
            if (id.startsWith(RESOURCE_ID)) {
                String strId = id.substring(RESOURCE_ID.length());
                if (strId.startsWith("0x"))
                    intId = Integer.parseInt(strId.substring(2), 16);
                else {
                    System.err.println("WARNING: non-hex resource id found: " + strId);
                    intId = Integer.parseInt(strId);
                }
            } else {
                if (id.startsWith("@+"))
                    id = id.substring(2);
                if (id.contains("/")) {
                    String[] parts = id.split("/");
                    Integer c = RLinker.getInstance().lookupConst(packageName, parts[0], parts[1]);
                    if (c != null)
                        intId = c;
                }
            }

            // Add a layout control with empty attributes.
            Map<String, Object> attrs = new HashMap<>();
            controls.add(new AndroidLayoutControl(intId, name, isSensitive(node), attrs, parentId));

            // Heuristic: if the name is unqualified, it comes from
            // android.view or android.widget ("Android Programming:
            // The Big Nerd Ranch Guide", chapter 32).
            if (name.lastIndexOf(".") == -1) {
                controls.add(new AndroidLayoutControl(intId, "android.view." + name, isSensitive(node), attrs, parentId));
                controls.add(new AndroidLayoutControl(intId, "android.widget." + name, isSensitive(node), attrs, parentId));
            }
        }

        NodeList l1 = node.getChildNodes();
        for (int i1 = 0; i1 < l1.getLength(); i1++)
            getUserControlsForNode(l1.item(i1), intId, controls);
    }

    private static boolean isSensitive(Node node) {
        String androidPassword = attrOrDefault(node, "android:password", null);
        if ((androidPassword != null) && androidPassword.equals("true"))
            return true;
        String inputT = attrOrDefault(node, "android:inputType", null);
        return (inputT != null) && (inputT.equals("textPassword") ||
                                    inputT.equals("textVisiblePassword") ||
                                    inputT.equals("textWebPassword") ||
                                    inputT.equals("numberPassword"));
    }

    // Read XML element attribute. On failure, return last default value.
    private static String attrOrDefault(Node node, String attr, String val) {
        NamedNodeMap attrs = node.getAttributes();
        if (attrs != null) {
            Node n = attrs.getNamedItem(attr);
            if (n != null)
                return n.getNodeValue();
        }
        return val;
    }

    private void handleException(Exception ex) {
        failures++;
        if (verbose)
            ex.printStackTrace();
    }

    private static class AndroidLayoutControl extends LayoutControl {
        private final int id;
        private final String viewClass;
        private final boolean sensitive;
        private final Map<String, Object> attrs;
        private final int parentId;
        private final String appRId = "";
        private final String androidRId = "";

        AndroidLayoutControl(int id, String viewClass, boolean sensitive, Map<String, Object> attrs, int parentId) {
            this.id = id;
            this.viewClass = viewClass;
            this.sensitive = sensitive;
            this.attrs = attrs;
            this.parentId = parentId;
        }

        public int getID() { return id; }
        public boolean isSensitive() { return sensitive; }
        public String getViewClassName() { return viewClass; }
        public int getParentID() { return parentId; }
        public String getAppRId() { return appRId; }
        public String getAndroidRId() { return androidRId; }
        public Map<String, Object> getAdditionalAttributes() { return attrs; }
    }
}
