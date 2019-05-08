package org.clyze.doop.common;

import javax.xml.parsers.*;
import org.xml.sax.*;
import org.xml.sax.helpers.*;

import java.util.*;
import java.io.*;

/**
 * Convert XML data to facts.
 */
public class XMLFactGenerator extends DefaultHandler {
    final XMLReader xmlReader;
    final JavaFactWriter writer;
    final String xmlPath;
    final String relativePath;
    final Stack<Integer> parents = new Stack<>();
    private static final int ROOT_NODE = -1;
    int nodeId = 0;

    private XMLFactGenerator(XMLReader xmlReader, JavaFactWriter writer, String xmlPath, String topDir) {
        this.xmlReader = xmlReader;
        this.writer = writer;
        this.xmlPath = xmlPath;
        this.relativePath = trimXMLPath(topDir);
    }

    /**
     * Process a directory containing XML files. Also process subdirectories.
     * @param dir     the directory to process
     * @param writer  the fact writer to use
     * @param topDir  the top directory to use when creating realtive
     *                paths (a prefix of the directory path)
     */
    public static void processDir(File dir, JavaFactWriter writer, String topDir) {
        File[] files = dir.listFiles();
        if (files != null)
            for (File f : files) {
                if (f.isDirectory())
                    processDir(f, writer, topDir);
                else if (f.isFile())
                    try {
                        String filePath = f.getCanonicalPath();
                        if (filePath.toLowerCase().endsWith(".xml")) {
                            System.out.println("Processing: " + filePath);
                            SAXParserFactory spf = SAXParserFactory.newInstance();
                            spf.setNamespaceAware(true);
                            XMLReader xmlReader = spf.newSAXParser().getXMLReader();
                            XMLFactGenerator gen = new XMLFactGenerator(xmlReader, writer, filePath, topDir);
                            gen.parse();
                        }
                    } catch (Exception ex) {
                        System.out.println("Error parsing file: " + f);
                        ex.printStackTrace();
                    }
            }
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
        writer.writeXMLNode(relativePath, nodeId, parentNodeId, namespaceURI, localName, qName);
        parents.push(nodeId);
        for (int idx = 0; idx < attrs.getLength(); idx++)
            writer.writeXMLNodeAttribute(relativePath, nodeId, idx, attrs.getLocalName(idx), attrs.getQName(idx), attrs.getValue(idx));
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
}
