package org.clyze.doop.common;

import java.util.*;
import java.io.*;
import javax.xml.parsers.*;
import org.xml.sax.*;
import org.xml.sax.helpers.*;
import static org.clyze.doop.common.JavaFactWriter.str;
import static org.clyze.doop.common.PredicateFile.*;

/**
 * Convert XML data to facts.
 */
public class XMLFactGenerator extends DefaultHandler {
    final XMLReader xmlReader;
    final Database db;
    final String xmlPath;
    final String relativePath;
    final Stack<Integer> parents = new Stack<>();
    private static final int ROOT_NODE = -1;
    int nodeId = 0;

    private XMLFactGenerator(XMLReader xmlReader, Database db, String xmlPath, String topDir) {
        this.xmlReader = xmlReader;
        this.db = db;
        this.xmlPath = xmlPath;
        this.relativePath = trimXMLPath(topDir);
    }

    /**
     * Process a directory containing XML files. Also process subdirectories.
     *
     * @param dir     the directory to process
     * @param db      the database object to use
     * @param topDir  the top directory to use when creating realtive
     *                paths (a prefix of the directory path)
     */
    public static void processDir(File dir, Database db, String topDir) {
        File[] files = dir.listFiles();
        if (files != null)
            for (File f : files) {
                if (f.isDirectory())
                    processDir(f, db, topDir);
                else if (f.isFile())
                    try {
                        String filePath = f.getCanonicalPath();
                        if (filePath.toLowerCase().endsWith(".xml"))
                            processFile(filePath, db, topDir);
                    } catch (Exception ex) {
                        System.out.println("Error parsing file: " + f);
                        ex.printStackTrace();
                    }
            }
    }

    /**
     * Process one XML file.
     *
     * @param xmlPath  the XML file to process
     * @param db      the database object to use
     * @param topDir   the top directory to use when creating realtive
     *                 paths (a prefix of the directory path)
     */
    public static void processFile(String xmlPath, Database db, String topDir)
        throws ParserConfigurationException, SAXException, IOException {
        System.out.println("Processing: " + xmlPath);
        SAXParserFactory spf = SAXParserFactory.newInstance();
        spf.setNamespaceAware(true);
        XMLReader xmlReader = spf.newSAXParser().getXMLReader();
        XMLFactGenerator gen = new XMLFactGenerator(xmlReader, db, xmlPath, topDir);
        gen.parse();
    }

    private void parse() throws IOException, SAXException {
        xmlReader.setContentHandler(this);
        xmlReader.parse(convertToFileURL(xmlPath));
    }

    private static String convertToFileURL(String filename) {
        String path = new File(filename).getAbsolutePath();
        if (File.separatorChar != '/')
            path = path.replace(File.separatorChar, '/');
        if (!path.startsWith("/"))
            path = "/" + path;
        return "file:" + path;
    }

    @Override
    public void startElement(String namespaceURI, String localName,
                             String qName, Attributes attrs) throws SAXException {
        nodeId++;
        int parentNodeId = parents.peek();
        writeXMLNode(relativePath, nodeId, parentNodeId, namespaceURI, localName, qName);
        parents.push(nodeId);
        for (int idx = 0; idx < attrs.getLength(); idx++)
            writeXMLNodeAttribute(relativePath, nodeId, idx, attrs.getLocalName(idx), attrs.getQName(idx), attrs.getValue(idx));
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        parents.pop();
    }

    @Override
    public void startDocument() throws SAXException {
        // Default parent for top-level node.
        parents.push(ROOT_NODE);
    }

    @Override
    public void endDocument() throws SAXException {
        if (!parents.empty() && (parents.peek() != ROOT_NODE))
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
        try {
            topDir = new File(topDir).getCanonicalPath();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        if (xmlPath.startsWith(topDir)) {
            return xmlPath.substring(topDir.length());
        } else {
            System.err.println("Cannot trim XML path " + xmlPath + ", it does not start with " + topDir);
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
    private void writeXMLNode(String file, int nodeId, int parentNodeId, String namespaceURI, String localName, String qName) {
        db.add(XMLNode, file, str(nodeId), str(parentNodeId), namespaceURI, localName, qName);
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
    private void writeXMLNodeAttribute(String file, int nodeId, int idx, String localName, String qName, String value) {
        db.add(XMLNodeAttribute, file, str(nodeId), str(idx), localName, qName, value);
    }
}
