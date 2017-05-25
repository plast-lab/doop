package org.clyze.deepdoop.datalog.component

import groovy.transform.InheritConstructors
import groovy.transform.ToString
import org.clyze.deepdoop.actions.AtomCollectingActor
import org.clyze.deepdoop.actions.IVisitable
import org.clyze.deepdoop.actions.PostOrderVisitor
import org.clyze.deepdoop.datalog.Program

class DependencyGraph {

	Map<String, Node> nodes
	List<Set<Node>> layers

	DependencyGraph(Program p) {
		nodes = [:]
		layers = []

		def acActor = new AtomCollectingActor()
		def acVisitor = new PostOrderVisitor<IVisitable>(acActor)
		p.accept(acVisitor)

		Set<String> globalAtoms = acActor.getDeclaringAtoms(p.globalComp).keySet()
		Set<String> handledGlobalAtoms = []

		p.props.each { prop ->
			Component fromComp = p.comps[prop.fromId]
			Component toComp = p.comps[prop.toId]

			def fromNode = getNode(fromComp)

			// Propagate to another component
			if (toComp != null)
				fromNode.addEdgeTo(getNode(toComp))
			// Propagate to global space
			else
				prop.preds.each { pred ->
					fromNode.addEdgeTo(getNode(pred.orig.name))
					handledGlobalAtoms.add(pred.orig.name)
				}

			// Dependencies from global space
			Set<String> fromGlobal = acActor.getUsedAtoms(fromComp).keySet()
			fromGlobal.retainAll(globalAtoms)

			fromGlobal.each { globalAtomName -> getNode(globalAtomName).addEdgeTo(fromNode) }
			handledGlobalAtoms += fromGlobal
		}

		// Topological sort
		Map<Node, Integer> inDegrees = [:]
		Set<Node> zeroInNodes = []
		nodes.values().each { n ->
			def inDegree = n.inEdges.size()
			inDegrees[n] = inDegree
			if (inDegree == 0) zeroInNodes << n
		}

		def curLayer = 0
		while (!zeroInNodes.isEmpty()) {
			Set<Node> newZeroInNodes = []
			def successorsExist = false
			zeroInNodes.each { n ->
				inDegrees.remove(n)
				if (layers.size() == curLayer)
					layers << ([n] as Set)
				else
					layers[curLayer] << n
				n.outEdges.each { succ ->
					successorsExist = true
					def newInDegree = inDegrees[succ] - 1
					inDegrees[succ] = newInDegree
					if (newInDegree == 0) newZeroInNodes << succ
				}
			}
			if (newZeroInNodes.isEmpty() && successorsExist)
				ErrorManager.error(ErrorId.DEP_CYCLE)
			zeroInNodes = newZeroInNodes
			curLayer++
		}

		globalAtoms.removeAll(handledGlobalAtoms)

		layers << (globalAtoms.collect { getNode(it) } as Set)
	}

	Node getNode(String name) {
		def key = "<$name>"
		def node = nodes[key]
		if (node == null) {
			node = new PredNode(name)
			nodes[key] = node
		}
		return node
	}

	Node getNode(Component comp) {
		def key = comp.name
		def node = nodes[key]
		if (node == null) {
			node = (comp instanceof CmdComponent ? new CmdNode(comp.name) : new CompNode(comp.name))
			nodes[key] = node
		}
		return node
	}


	abstract static class Node {
		String name
		Set<Node> inEdges = []
		Set<Node> outEdges = []

		Node(String name) {
			this.name = name
			this.inEdges = []
			this.outEdges = []
		}

		void addEdgeTo(Node toNode) {
			outEdges << toNode
			toNode.inEdges << this
		}
	}

	@InheritConstructors
	@ToString(includeSuper = true)
	static class PredNode extends Node {}

	@InheritConstructors
	@ToString(includeSuper = true)
	static class CompNode extends Node {}

	@InheritConstructors
	@ToString(includeSuper = true)
	static class CmdNode extends Node {}
}
