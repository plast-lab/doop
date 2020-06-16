package org.clyze.doop.common;

import java.util.*;
import java.io.*;
import javax.xml.parsers.*;

import org.xml.sax.*;
import org.xml.sax.helpers.*;
import static org.clyze.doop.common.JavaFactWriter.str;
import static org.clyze.doop.common.PredicateFile.*;
import org.clyze.utils.JHelper;

/**
 * Convert XML data to facts. Converts some extra logic for
 * Android-specific attributes.
 */
@SuppressWarnings("RedundantThrows")
public class XMLFactGenerator extends DefaultHandler {
    private final String[] ID_PREFIXES = new String[] { "@id/", "@android:id/" };
    @SuppressWarnings("FieldCanBeLocal")
    private final String LAYOUT_PREFIX = "@layout/";

    private final boolean debug;
    private final Database db;
    private final File xmlFile;
    private final String relativePath;
    private final Stack<Integer> parents = new Stack<>();
    // This should match the constant in the XML logic.
    private static final int ROOT_NODE = -1;
    private int nodeId = 0;
    // Last <string> node.
    private Node lastStringNode = null;
    // Contains the inner data of an element.
    private String xmlData = null;

    private XMLFactGenerator(Database db, File xmlFile, String topDir, boolean debug) {
        this.db = db;
        this.xmlFile = xmlFile;
        this.relativePath = trimXMLPath(topDir);
        this.debug = debug;
    }

    /**
     * Process a directory containing XML files. Also process subdirectories.
     *
     * @param dir     the directory to process
     * @param db      the database object to use
     * @param debug   if true, print debugging information
     * @param topDir  the top directory to use when creating relative
     *                paths (a prefix of the directory path)
     */
    public static void processDir(File dir, Database db, String topDir, boolean debug) {
        File[] files = dir.listFiles();
        if (files != null)
            for (File f : files) {
                if (f.isDirectory())
                    processDir(f, db, topDir, debug);
                else if (f.isFile()) {
                    String filePath = f.getAbsolutePath();
                    if (filePath.toLowerCase().endsWith(".xml")) {
                        if (debug)
                            System.out.println("Processing: " + f);
                        // Skip original AndroidManifest.xml (binary XML data).
                        if (!f.getAbsolutePath().endsWith("/original/AndroidManifest.xml"))
                            processFile(f, db, topDir, debug);
                    }
                }
            }
    }

    /**
     * Process one XML file.
     *
     * @param xmlFile  the XML file to process
     * @param db       the database object to use
     * @param topDir   the top directory to use when creating realtive
     *                 paths (a prefix of the directory path)
     * @param debug    if true, show debug messages
     */
    public static void processFile(File xmlFile, Database db, String topDir, boolean debug) {
        try {
            XMLFactGenerator gen = new XMLFactGenerator(db, xmlFile, topDir, debug);
            gen.parse(debug);
        } catch (ParserConfigurationException | SAXException | IOException ex) {
            String msg = ex.getMessage();
            if (debug)
                System.err.println("Error parsing " + xmlFile + ": " + msg);
            // ex.printStackTrace();
        }
    }

    private void parse(boolean debug) throws IOException, SAXException, ParserConfigurationException {
        SAXParserFactory spf = SAXParserFactory.newInstance();
        spf.setNamespaceAware(true);
        XMLReader xmlReader = spf.newSAXParser().getXMLReader();
        xmlReader.setContentHandler(this);
        // If not in debug mode, replace verbose error handler with dummy one.
        if (!debug)
            xmlReader.setErrorHandler(new ErrorHandler() {
                    public void error(SAXParseException exception) { }
                    public void fatalError(SAXParseException exception) { }
                    public void warning(SAXParseException exception) { }
            });

        try (FileInputStream is1 = new FileInputStream(xmlFile)) {
            xmlReader.parse(new InputSource(is1));
        } catch (SAXParseException ex) {
            if (debug)
                System.err.println("XML processing may fail for " + xmlFile.getAbsolutePath() + ", trying automatic encoding conversion...");
            JHelper.ensureUTF8(xmlFile.getAbsolutePath(), debug);
            try (FileInputStream is2 = new FileInputStream(xmlFile)) {
                xmlReader.parse(new InputSource(is2));
            }
        }
    }

    @Override
    public void startElement(String namespaceURI, String localName,
                             String qName, Attributes attrs) throws SAXException {
        nodeId++;
        int parentNodeId = parents.peek();
        String sNodeId = str(nodeId);
        writeXMLNode(relativePath, sNodeId, parentNodeId, namespaceURI, localName, qName);
        for (int idx = 0; idx < attrs.getLength(); idx++)
            writeXMLNodeAttribute(relativePath, sNodeId, idx, attrs.getLocalName(idx), attrs.getQName(idx), attrs.getValue(idx));
        parents.push(nodeId);
        if (qName.equals("string"))
            lastStringNode = new Node(relativePath, sNodeId);
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        parents.pop();

        if (lastStringNode != null) {
            if (xmlData != null)
                db.add(XMLNodeData, lastStringNode.file, lastStringNode.nodeId, xmlData);
            lastStringNode = null;
        }
    }

    @Override
    public void startDocument() throws SAXException {
        // Default parent for top-level node.
        parents.push(ROOT_NODE);
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (lastStringNode != null)
            xmlData = new String(ch, start, length);
    }

    @Override
    public void endDocument() throws SAXException {
        if (parents.empty() || (parents.peek() != ROOT_NODE))
            throw new RuntimeException("Internal error: corrupt node traversal, non-empty parent = " + parents.peek());
    }

    /**
     * Convert the XML path to a local one (relative to topDir), so
     * that exchanged facts do not leak filesystem information.
     *
     * @param topDir    the top directory that is a prefix of the XML path
     * @return          a local XML path
     */
    private String trimXMLPath(String topDir) {
        topDir = new File(topDir).getAbsolutePath();
        String xmlPath = xmlFile.getAbsolutePath();
        if (xmlPath.startsWith(topDir)) {
            return xmlPath.substring(topDir.length());
        } else {
            // System.err.println("Cannot trim XML path " + xmlPath + ", it does not start with " + topDir);
            return xmlPath;
        }
    }

    /**
     * Write XML node as facts tuple.
     *
     * @param file           the .xml file containing the node
     * @param nodeId         a unique identifier for the node (per file)
     * @param parentNodeId   a unique identifier for the parent node (per file)
     * @param namespaceURI   the namespace URI
     * @param localName      the local name of the node
     * @param qName          the qualified name of the node
     */
    private void writeXMLNode(String file, String nodeId, int parentNodeId, String namespaceURI, String localName, String qName) {
        db.add(XMLNode, file, nodeId, str(parentNodeId), namespaceURI, localName, qName);
    }

    /**
     * Write XML node attribute as facts tuple.
     *
     * @param file           the .xml file containing the node for the attribute
     * @param nodeId         a unique identifier for the node (per file)
     * @param localName      the local name of the attribute
     * @param qName          the qualified name of the attribute
     * @param value          the value of the attribute
     */
    private void writeXMLNodeAttribute(String file, String nodeId, int idx, String localName, String qName, String value) {
        db.add(XMLNodeAttribute, file, nodeId, str(idx), localName, qName, value);
        // Register Android ids by extracting their labels.
        if (qName.equals("android:id")) {
            boolean handled = false;
            for (String prefix : ID_PREFIXES)
                if (value.startsWith(prefix)) {
                    db.add(ANDROID_ID, file, nodeId, value, prefix, value.substring(prefix.length()));
                    handled = true;
                } else if (value.startsWith("@+id/") && debug) {
                    System.err.println("WARNING: non-constant id found in: " + value);
                }
            if (!handled) {
                if (debug)
                    System.err.println("WARNING: could not process Android id: " + value);
                db.add(ANDROID_ID, file, nodeId, value, "-", value);
            }
        } else if (qName.equals("layout")) {
            if (value.startsWith(LAYOUT_PREFIX)) {
                db.add(ANDROID_INCLUDE_XML, file, nodeId, value.substring(LAYOUT_PREFIX.length()));
            } else if (debug)
                System.err.println("WARNING: ignoring layout=" + value);
        }
    }

    private static class Node {
        final String file;
        final String nodeId;
        Node(String file, String nodeId) {
            this.file = file;
            this.nodeId = nodeId;
        }
    }
}
