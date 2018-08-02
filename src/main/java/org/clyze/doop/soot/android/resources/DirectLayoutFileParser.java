/*******************************************************************************
 * Copyright (c) 2012 Secure Software Engineering Group at EC SPRIDE.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v2.1
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 *
 * Contributors: Christian Fritz, Steven Arzt, Siegfried Rasthofer, Eric
 * Bodden, and others.
 ******************************************************************************/
package org.clyze.doop.soot.android.resources;

import org.clyze.doop.soot.android.axml.AXmlAttribute;
import org.clyze.doop.soot.android.axml.AXmlHandler;
import org.clyze.doop.soot.android.axml.AXmlNode;
import org.clyze.doop.soot.android.axml.parsers.AXML20Parser;
import pxb.android.axml.AxmlVisitor;

import org.clyze.doop.soot.android.resources.ARSCFileParser.AbstractResource;
import org.clyze.doop.soot.android.resources.ARSCFileParser.StringResource;

import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Parser for exporting info from the layout XML files inside an android application.
 * Does not use Soot analysis at all--just string constants in the XML. Copies much
 * of the original LayoutFileParser.java
 *
 * @author Steven Arzt
 * @author Yannis Smaragdakis
 *
 */
public class DirectLayoutFileParser extends AbstractResourceParser {

    private static final boolean DEBUG = true;

    private final Map<String, Set<PossibleLayoutControl>> userControls = new HashMap<String, Set<PossibleLayoutControl>>();
    private final Map<String, Set<String>> callbackMethods = new HashMap<String, Set<String>>();
    private final Map<String, Set<String>> includeDependencies = new HashMap<String, Set<String>>();
    private static final Map<String, AXmlHandler> xmlHandlerMap = new HashMap<>();
    private final String packageName;
    private final ARSCFileParser resParser;

    private boolean loadAdditionalAttributes = false;

    private final static int TYPE_NUMBER_VARIATION_PASSWORD = 0x00000010;
    private final static int TYPE_TEXT_VARIATION_PASSWORD = 0x00000080;
    private final static int TYPE_TEXT_VARIATION_VISIBLE_PASSWORD = 0x00000090;
    private final static int TYPE_TEXT_VARIATION_WEB_PASSWORD = 0x000000e0;

    public DirectLayoutFileParser(String packageName, ARSCFileParser resParser) {
        this.packageName = packageName;
        this.resParser = resParser;
    }

    private Set<String> getPossibleLayoutClassNames(String className) {
        Set<String> possibleNames = new HashSet<String>();
        // Cut off some junk returned by the parser
        if (className.startsWith(";"))
            className = className.substring(1);

        if (className.contains("(") || className.contains("<") || className.contains("/")) {
            System.err.println("Invalid class name " + className);
            return null;
        }

        if (!className.contains(".")) {  // not a fully qualified class name! Need to guess.
            if (!packageName.isEmpty())
                possibleNames.add(packageName + "." + className);
            possibleNames.add("android.view." + className);
            possibleNames.add("android.widget." + className);
            possibleNames.add("android.webkit." + className);
        }
        else
            possibleNames.add(className);

        return possibleNames;
    }

    /**
     * Checks whether the given namespace belongs to the Android operating system
     * @param ns The namespace to check
     * @return True if the namespace belongs to Android, otherwise false
     */
    private boolean isAndroidNamespace(String ns) {
        if (ns == null)
            return false;
        ns = ns.trim();
        if (ns.startsWith("*"))
            ns = ns.substring(1);
        if (!ns.equals("http://schemas.android.com/apk/res/android"))
            return false;
        return true;
    }

    private <X,Y> void addToMapSet(Map<X, Set<Y>> target, X layoutFile, Y callback) {
        if (target.containsKey(layoutFile))
            target.get(layoutFile).add(callback);
        else {
            Set<Y> callbackSet = new HashSet<Y>();
            callbackSet.add(callback);
            target.put(layoutFile, callbackSet);
        }
    }

    /**
     * Adds a callback method found in an XML file to the result set
     * @param layoutFile The XML file in which the callback has been found
     * @param callback The callback found in the given XML file
     */
    private void addCallbackMethod(String layoutFile, String callback) {
        addToMapSet(callbackMethods, layoutFile, callback);

        // Recursively process any dependencies we might have collected before
        // we have processed the target
        if (includeDependencies.containsKey(layoutFile))
            for (String target : includeDependencies.get(layoutFile))
                addCallbackMethod(target, callback);
    }


    public void registerLayoutFilesDirect(final String fileName) {
        handleAndroidResourceFiles(fileName, /*classes,*/
                null,
                new IResourceHandler()
                {
                    @Override
                    public void handleResourceFile(final String fileName, Set<String> fileNameFilter, InputStream stream) {
                        // We only process valid layout XML files
                        if (!fileName.startsWith("res/layout"))
                            return;
                        if (!fileName.endsWith(".xml")) {
                            System.err.println("Skipping file " + fileName + " in layout folder...");
                            return;
                        }

                        // Get the fully-qualified class name
                        String entryClass = fileName.substring(0, fileName.lastIndexOf("."));
                        if (!packageName.isEmpty())
                            entryClass = packageName + "." + entryClass;

                        // We are dealing with resource files
                        if (!fileName.startsWith("res/layout"))
                            return;
                        if (fileNameFilter != null) {
                            boolean found = false;
                            for (String s : fileNameFilter)
                                if (s.equalsIgnoreCase(entryClass)) {
                                    found = true;
                                    break;
                                }
                            if (!found)
                                return;
                        }

                        try {
                            AXmlHandler handler = new AXmlHandler(stream, new AXML20Parser());
                            xmlHandlerMap.put(fileName, handler);
                        }
                        catch (Exception ex) {
                            System.err.println("Could not read binary XML file: " + ex.getMessage());
                            ex.printStackTrace();
                        }
                    }
                });
    }
    /**
     * Parses all layout XML files in the given APK file and finds the IDs of
     * the user controls in it.
     * @param fileName The APK file in which to look for user controls
     */
    public void parseLayoutFileDirect(final String fileName) {
        handleAndroidResourceFiles(fileName, /*classes,*/
                null,
                new IResourceHandler()
                {
                    @Override
                    public void handleResourceFile(final String fileName, Set<String> fileNameFilter, InputStream stream) {
                        // We only process valid layout XML files
                        if (!fileName.startsWith("res/layout"))
                            return;
                        if (!fileName.endsWith(".xml")) {
                            System.err.println("Skipping file " + fileName + " in layout folder...");
                            return;
                        }

                        // Get the fully-qualified class name
                        String entryClass = fileName.substring(0, fileName.lastIndexOf("."));
                        if (!packageName.isEmpty())
                            entryClass = packageName + "." + entryClass;

                        // We are dealing with resource files
                        if (!fileName.startsWith("res/layout"))
                            return;
                        if (fileNameFilter != null) {
                            boolean found = false;
                            for (String s : fileNameFilter)
                                if (s.equalsIgnoreCase(entryClass)) {
                                    found = true;
                                    break;
                                }
                            if (!found)
                                return;
                        }

                        try {
                            AXmlHandler handler = new AXmlHandler(stream, new AXML20Parser());
                            parseLayoutNode(fileName, handler.getDocument().getRootNode(), null);

                            System.out.println("Found " + userControls.size() + " layout controls in file "
                                    + fileName);
                        }
                        catch (Exception ex) {
                            System.err.println("Could not read binary XML file: " + ex.getMessage());
                            ex.printStackTrace();
                        }
                    }
                });
    }

    /**
     * Parses the layout file with the given root node
     * @param layoutFile The full path and file name of the file being parsed
     * @param rootNode The root node from where to start parsing
     */
    private void parseLayoutNode(String layoutFile, AXmlNode rootNode, PossibleLayoutControl parent) throws Exception {
        if (rootNode.getTag() == null || rootNode.getTag().isEmpty()) {
            System.err.println("Encountered a null or empty node name "
                    + "in file " + layoutFile + ", skipping node...");
            return;
        }

        String tname = rootNode.getTag().trim();
        if (tname.equals("dummy")) {
            // dummy root node, ignore it
        }
        // Check for inclusions
        else if (tname.equals("include")) {
            String includedFile = parseIncludeAttributes(layoutFile, rootNode, parent);
            System.out.println("Included file: " + includedFile);
            AXmlHandler handler = xmlHandlerMap.get(includedFile);
            parseLayoutNode(includedFile, handler.getDocument().getRootNode(), parent);
        }
        // The "merge" tag merges the next hierarchy level into the current
        // one for flattening hierarchies.
        else if (tname.equals("merge"))  {
            // do not consider any attributes of this elements, just
            // continue with the children
        }
        else if (tname.equals("fragment"))  {
            final AXmlAttribute<?> attr = rootNode.getAttribute("name");
            if (attr == null)
                System.err.println("Fragment without class name detected");
            else {
                if (attr.getType() != AxmlVisitor.TYPE_STRING)
                    System.err.println("Invalid target resource "+attr.getValue()+"for fragment class value");
                Set<String> possibleNames = getPossibleLayoutClassNames(attr.getValue().toString());
                for (String possibleClassName : possibleNames) {
                    parseLayoutAttributes(layoutFile, possibleClassName, rootNode, parent);
                } // Is this correct? It's not what the Soot code did, but I think it's buggy.
            }
        }
        else {
            final Set<String> childClassNames = getPossibleLayoutClassNames(tname);
            for (String childClassName : childClassNames) {
                parent = parseLayoutAttributes(layoutFile, childClassName, rootNode, parent);
            }
        }

        // Parse the child nodes
        for (AXmlNode childNode : rootNode.getChildren())
            parseLayoutNode(layoutFile, childNode, parent);
    }

    /**
     * Parses the attributes required for a layout file inclusion
     * @param layoutFile The full path and file name of the file being parsed
     * @param rootNode The AXml node containing the attributes
     */
    private String parseIncludeAttributes(String layoutFile, AXmlNode rootNode, PossibleLayoutControl parent) {
        for (Entry<String, AXmlAttribute<?>> entry : rootNode.getAttributes().entrySet()) {
            String attrName = entry.getKey().trim();
            AXmlAttribute<?> attr = entry.getValue();

            if (attrName.equals("layout")) {
                if ((attr.getType() == AxmlVisitor.TYPE_REFERENCE || attr.getType() == AxmlVisitor.TYPE_INT_HEX)
                        && attr.getValue() instanceof Integer) {
                    // We need to get the target XML file from the binary manifest
                    AbstractResource targetRes = resParser.findResource((Integer) attr.getValue());

                    if (targetRes == null) {
                        System.err.println("Target resource " + attr.getValue() + " for layout include not found");
                        return null;
                    }
                    if (!(targetRes instanceof StringResource)) {
                        System.err.println("Invalid target node for include tag in layout XML, was "
                                + targetRes.getClass().getName());
                        return null;
                    }
                    String targetFile = ((StringResource) targetRes).getValue();

                    // If we have already processed the target file, we can
                    // simply copy the callbacks we have found there
                    if (callbackMethods.containsKey(targetFile))
                        for (String callback : callbackMethods.get(targetFile))
                            addCallbackMethod(layoutFile, callback);
                    else {
                        // We need to record a dependency to resolve later
                        addToMapSet(includeDependencies, targetFile, layoutFile);
                    }

                    return targetFile;
                }
            }
        }
        return null;
    }

    /**
     * Parses the layout attributes in the given AXml node
     * @param layoutFile The full path and file name of the file being parsed
     * @param layoutClassName The class name for which the attributes are parsed
     * @param rootNode The AXml node containing the attributes
     */
    private PossibleLayoutControl parseLayoutAttributes(String layoutFile, String layoutClassName, AXmlNode rootNode, PossibleLayoutControl parent) {
        boolean isSensitive = false;
        int id = -1;
        Map<String, Object> additionalAttributes = loadAdditionalAttributes
                ? new HashMap<String, Object>() : null;

        for (Entry<String, AXmlAttribute<?>> entry : rootNode.getAttributes().entrySet()) {
            if (entry.getKey() == null)
                continue;

            String attrName = entry.getKey().trim();
            AXmlAttribute<?> attr = entry.getValue();

            // On obfuscated Android malware, the attribute name may be empty
            if (attrName.isEmpty())
                continue;

            // Check that we're actually working on an android attribute
            if (!isAndroidNamespace(attr.getNamespace()))
                continue;

            // Read out the field data
            if (attrName.equals("id")
                    && (attr.getType() == AxmlVisitor.TYPE_REFERENCE || attr.getType() == AxmlVisitor.TYPE_INT_HEX))
                id = (Integer) attr.getValue();
            else if (attrName.equals("password")) {
                if (attr.getType() == AxmlVisitor.TYPE_INT_HEX)
                    isSensitive = ((Integer) attr.getValue()) != 0; // -1 for true, 0 for false
                else if (attr.getType() == AxmlVisitor.TYPE_INT_BOOLEAN)
                    isSensitive = (Boolean) attr.getValue();
                else
                    throw new RuntimeException("Unknown representation of boolean data type");
            }
            else if (!isSensitive && attrName.equals("inputType") && attr.getType() == AxmlVisitor.TYPE_INT_HEX) {
                int tp = (Integer) attr.getValue();
                isSensitive = ((tp & TYPE_NUMBER_VARIATION_PASSWORD) == TYPE_NUMBER_VARIATION_PASSWORD)
                        || ((tp & TYPE_TEXT_VARIATION_PASSWORD) == TYPE_TEXT_VARIATION_PASSWORD)
                        || ((tp & TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) == TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)
                        || ((tp & TYPE_TEXT_VARIATION_WEB_PASSWORD) == TYPE_TEXT_VARIATION_WEB_PASSWORD);
            }
            else if (isActionListener(attrName)
                    && attr.getType() == AxmlVisitor.TYPE_STRING
                    && attr.getValue() instanceof String) {
                String strData = ((String) attr.getValue()).trim();
                addCallbackMethod(layoutFile, strData);
            }
            else if (attr.getType() == AxmlVisitor.TYPE_STRING && attrName.equals("text")) {
                // To avoid unrecognized attribute for "text" field
            }
            else if (loadAdditionalAttributes) {
                additionalAttributes.put(attrName, attr.getValue());
            }
            else if (DEBUG && attr.getType() == AxmlVisitor.TYPE_STRING) {
                System.out.println("Found unrecognized XML attribute:  " + attrName + " with value: " + attr.getValue());
            }
        }

        // Register the new user control
        PossibleLayoutControl currentLayoutControl;
        if (parent != null) {
            currentLayoutControl = new PossibleLayoutControl(
                    id, layoutClassName, isSensitive, additionalAttributes, parent.getID());
        }
        else {
            currentLayoutControl = new PossibleLayoutControl(
                    id, layoutClassName, isSensitive, additionalAttributes, -1);
        }
        addToMapSet(this.userControls, layoutFile, currentLayoutControl);
        return currentLayoutControl;
    }

    /**
     * Checks whether this name is the name of a well-known Android listener
     * attribute. This is a function to allow for future extension.
     * @param name The attribute name to check. This name is guaranteed to
     * be in the android namespace.
     * @return True if the given attribute name corresponds to a listener,
     * otherwise false.
     */
    private boolean isActionListener(String name) {
        return name.equals("onClick");
    }

    /**
     * Gets the user controls found in the layout XML file. The result is a
     * mapping from the id to the respective layout control.
     * @return The layout controls found in the XML file.
     */
    public Map<Integer, PossibleLayoutControl> getUserControlsByID() {
        Map<Integer, PossibleLayoutControl> res = new HashMap<Integer, PossibleLayoutControl>();
        for (Set<PossibleLayoutControl> controls : this.userControls.values())
            for (PossibleLayoutControl lc : controls)
                res.put(lc.getID(), lc);
        return res;
    }

    /**
     * Gets the user controls found in the layout XML file. The result is a
     * mapping from the file name in which the control was found to the
     * respective layout control.
     * @return The layout controls found in the XML file.
     */
    public Map<String, Set<PossibleLayoutControl>> getUserControls() {
        return this.userControls;
    }

    /**
     * Gets the callback methods found in the layout XML file. The result is a
     * mapping from the file name to the set of found callback methods.
     * @return The callback methods found in the XML file.
     */
    public Map<String, Set<String>> getCallbackMethods() {
        return this.callbackMethods;
    }

    /**
     * Sets whether the parser should load all additional attributes as well. If
     * this option is disabled, it only loads those well-known attributes that
     * influence the data flow analysis.
     * @param loadAdditionalAttributes True to load all attributes present in the
     * layout XML files, false to only load the ones required for FlowDroid.
     */
    public void setLoadAdditionalAttributes(boolean loadAdditionalAttributes) {
        this.loadAdditionalAttributes = loadAdditionalAttributes;
    }

}
