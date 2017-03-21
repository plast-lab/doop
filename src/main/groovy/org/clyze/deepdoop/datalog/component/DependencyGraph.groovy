package org.clyze.deepdoop.datalog.component

import org.clyze.deepdoop.actions.*
import org.clyze.deepdoop.datalog.Program
import org.clyze.deepdoop.system.*

class DependencyGraph {

	Map<String, Node> _nodes
	List<Set<Node>>   _layers

	DependencyGraph(Program p) {
		_nodes = [:]

		def acActor = new AtomCollectingActor()
		def acVisitor = new PostOrderVisitor<IVisitable>(acActor)
		p.accept(acVisitor)

		Set<String> globalAtoms = acActor.getDeclaringAtoms(p.globalComp).keySet()
		Set<String> handledGlobalAtoms = []

		p.props.each{ prop ->
			Component fromComp = p.comps.get(prop.fromId)
			Component toComp   = p.comps.get(prop.toId)

			def fromNode = getNode(fromComp)

			// Propagate to another component
			if (toComp != null)
				fromNode.addEdgeTo(getNode(toComp))
			// Propagate to global space
			else
				prop.preds.each{ pred ->
					fromNode.addEdgeTo(getNode(pred.name()))
					handledGlobalAtoms.add(pred.name())
				}

			// Dependencies from global space
			Set<String> fromGlobal  = acActor.getUsedAtoms(fromComp).keySet()
			fromGlobal.retainAll(globalAtoms)

			fromGlobal.each{ globalAtomName -> getNode(globalAtomName).addEdgeTo(fromNode) }
			handledGlobalAtoms.addAll(fromGlobal)
		}

		// Topological sort
		Map<Node, Integer> inDegrees = [:]
		Set<Node> zeroInNodes = []
		_nodes.values().each{ n ->
			def inDegree = n.inEdges.size()
			inDegrees[n] = inDegree
			if (inDegree == 0) zeroInNodes.add(n)
		}

		def curLayer = 0
		_layers = []
		while (!zeroInNodes.isEmpty()) {
			Set<Node> newZeroInNodes = []
			def successorsExist = false
			zeroInNodes.each{ n ->
				inDegrees.remove(n)
				if (_layers.size() == curLayer) {
					Set<Node> layerNodes = []
					layerNodes.add(n)
					_layers.add(layerNodes)
				}
				else {
					def layerNodes = _layers.get(curLayer)
					layerNodes.add(n)
				}
				n.outEdges.each{ succ ->
					successorsExist = true
					def newInDegree = inDegrees.get(succ) - 1
					inDegrees[succ] = newInDegree
					if (newInDegree == 0) newZeroInNodes.add(succ)
				}
			}
			if (newZeroInNodes.isEmpty() && successorsExist)
				ErrorManager.error(ErrorId.DEP_CYCLE)
			zeroInNodes = newZeroInNodes
			curLayer++
		}

		globalAtoms.removeAll(handledGlobalAtoms)
		Set<Node> lastGlobalNodes = []
		globalAtoms.each{ globalAtom -> lastGlobalNodes.add(getNode(globalAtom)) }
		_layers.add(lastGlobalNodes)
	}

	List<Set<Node>> getLayers() { return _layers }

	Node getNode(String name) {
		def key = "<$name>"
		def node = _nodes[key]
		if (node == null) {
			node = new PredNode(name)
			_nodes[key] = node
		}
		return node
	}
	Node getNode(Component comp) {
		def key = comp.name
		def node = _nodes[key]
		if (node == null) {
			node = (comp instanceof CmdComponent ? new CmdNode(comp.name) : new CompNode(comp.name))
			_nodes[key] = node
		}
		return node
	}


	abstract static class Node {

		public final String    name
		public final Set<Node> inEdges
		public final Set<Node> outEdges

		Node(String name) {
			this.name     = name
			this.inEdges  = []
			this.outEdges = []
		}

		void addEdgeTo(Node toNode) {
			outEdges.add(toNode)
			toNode.inEdges.add(this)
		}
	}
	static class PredNode extends Node {
		PredNode(String name) { super(name) }
		String toString() { return name }
	}
	static class CompNode extends Node {
		CompNode(String name) { super(name) }
		String toString() { return "<$name>" }
	}
	static class CmdNode extends Node {
		CmdNode(String name) { super(name) }
		String toString() { return "{$name}" }
	}
}
