package deepdoop.datalog.component;

import deepdoop.actions.*;
import deepdoop.datalog.Program;
import deepdoop.datalog.DeepDoopException;
import deepdoop.datalog.element.atom.IAtom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DependencyGraph {

	Map<String, Node> _nodes;
	List<Set<Node>>   _layers;

	public DependencyGraph(Program p) {
		_nodes = new HashMap<>();

		AtomCollectingActor acActor = new AtomCollectingActor();
		PostOrderVisitor<IVisitable> acVisitor = new PostOrderVisitor<>(acActor);
		p.accept(acVisitor);

		Set<String> globalAtoms = acActor.getDeclaringAtoms(p.globalComp).keySet();
		Set<String> handledGlobalAtoms = new HashSet<>();

		for (Propagation prop : p.props) {
			Node fromNode = getCompNode(prop.fromId);

			// Propagate to another component
			if (prop.toId != null)
				fromNode.addEdgeTo(getCompNode(prop.toId));
			// Propagate to global space
			else
				for (IAtom pred : prop.preds) {
					IAtom newPred = (IAtom) pred.accept(new InitVisitingActor(prop.fromId, null, new HashSet<>()));
					fromNode.addEdgeTo(getPredNode(newPred.name()));
					handledGlobalAtoms.add(newPred.name());
				}

			// Dependencies from global space
			Component fromComp = p.comps.get(prop.fromId);
			Set<String> fromGlobal  = acActor.getUsedAtoms(fromComp).keySet();
			fromGlobal.retainAll(globalAtoms);

			for (String globalAtomName : fromGlobal)
				getPredNode(globalAtomName).addEdgeTo(fromNode);
			handledGlobalAtoms.addAll(fromGlobal);
		}

		// Topological sort
		Map<Node, Integer> inDegrees = new HashMap<>();
		Set<Node> zeroInNodes = new HashSet<>();
		for (Node n : _nodes.values()) {
			int inDegree = n._inEdges.size();
			inDegrees.put(n, inDegree);
			if (inDegree == 0) zeroInNodes.add(n);
		}

		int curLayer = 0;
		_layers = new ArrayList<>();
		while (!zeroInNodes.isEmpty()) {
			Set<Node> newZeroInNodes = new HashSet<>();
			boolean successorsExist = false;
			for (Node n : zeroInNodes) {
				inDegrees.remove(n);
				if (_layers.size() == curLayer) {
					Set<Node> layerNodes = new HashSet<>();
					layerNodes.add(n);
					_layers.add(layerNodes);
				}
				else {
					Set<Node> layerNodes = _layers.get(curLayer);
					layerNodes.add(n);
				}
				for (Node succ : n._outEdges) {
					successorsExist = true;
					int newInDegree = inDegrees.get(succ) - 1;
					inDegrees.put(succ, newInDegree);
					if (newInDegree == 0) newZeroInNodes.add(succ);
				}
			}
			if (newZeroInNodes.isEmpty() && successorsExist)
				throw new DeepDoopException("Cycle detected in the dependency graph of components");
			zeroInNodes = newZeroInNodes;
			curLayer++;
		}

		globalAtoms.removeAll(handledGlobalAtoms);
		Set<Node> lastGlobalNodes = new HashSet<>();
		for (String globalAtom : globalAtoms) lastGlobalNodes.add(getPredNode(globalAtom));
		_layers.add(lastGlobalNodes);

		//System.out.println(_layers);
		//for (Node n : _nodes.values()) n.print();
	}

	Node getCompNode(String name) {
		name = "<" + name + ">";
		Node node = _nodes.get(name);
		if (node == null) {
			node = new Node(Node.Type.COMP, name);
			_nodes.put(name, node);
		}
		return node;
	}
	Node getPredNode(String name) {
		Node node = _nodes.get(name);
		if (node == null) {
			node = new Node(Node.Type.COMP, name);
			_nodes.put(name, node);
		}
		return node;
	}


	static class Node {

		enum Type { PRED, COMP, CMD }

		Type      _type;
		String    _name;
		Set<Node> _inEdges;
		Set<Node> _outEdges;

		Node(Type type, String name) {
			_type = type;
			_name = name;
			_inEdges = new HashSet<>();
			_outEdges = new HashSet<>();
		}

		void addEdgeTo(Node toNode) {
			this._outEdges.add(toNode);
			toNode._inEdges.add(this);
		}

		void print() {
			for (Node n : _outEdges) System.out.println(_name + " -----> "+ n._name);
		}

		@Override
		public String toString() {
			return _name;
		}
	}
}
