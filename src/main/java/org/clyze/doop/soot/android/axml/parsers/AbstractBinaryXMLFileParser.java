package org.clyze.doop.soot.android.axml.parsers;

import org.clyze.doop.soot.android.axml.AXmlDocument;
import org.clyze.doop.soot.android.axml.AXmlNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;


/**
 * Abstract base class for all binary XML file parsers
 * 
 * @author Steven Arzt
 */
public abstract class AbstractBinaryXMLFileParser implements IBinaryXMLFileParser {

	/**
	 * Map containing lists of nodes sharing the same <code>tag</code>.
	 * The <code>tag</code> is the key to access the list.
	 */
	protected HashMap<String, ArrayList<AXmlNode>> nodesWithTag = new HashMap<String, ArrayList<AXmlNode>>();

	/**
	 * The xml document.
	 */
	protected AXmlDocument document = new AXmlDocument();
	
	/**
	 * Adds a pointer to the given <code>node</code> with the key <code>tag</code>.  
	 * 
	 * @param	tag		the node's tag
	 * @param	node	the node being pointed to
	 */
	protected void addPointer(String tag, AXmlNode node) {
		if(!this.nodesWithTag.containsKey(tag)) this.nodesWithTag.put(tag, new ArrayList<AXmlNode>());
		this.nodesWithTag.get(tag).add(node);
	}
	
	@Override
	public AXmlDocument getDocument() {
		return this.document;
	}
	
	@Override
	public List<AXmlNode> getNodesWithTag(String tag) {
		if(this.nodesWithTag.containsKey(tag))
			return new ArrayList<AXmlNode>(this.nodesWithTag.get(tag));
		else
			return Collections.emptyList();
	}

}
