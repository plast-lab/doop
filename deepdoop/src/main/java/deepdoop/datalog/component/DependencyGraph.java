package deepdoop.datalog.component;

import deepdoop.actions.AtomCollectingActor;
import deepdoop.actions.IVisitable;
import deepdoop.actions.PostOrderVisitor;
import deepdoop.datalog.Program;
import deepdoop.datalog.DeepDoopException;
import deepdoop.datalog.element.atom.IAtom;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class DependencyGraph {

	Map<String, Node> _nodes;

	public DependencyGraph(Program p) {
		_nodes = new HashMap<>();

		AtomCollectingActor acActor = new AtomCollectingActor();
		PostOrderVisitor<IVisitable> visitor = new PostOrderVisitor<>(acActor);
		p.accept(visitor);
		for (Propagation prop : p.props) {
			Node fromNode = getNode(prop.fromId, false);

			if (prop.toId != null)
				fromNode.addEdgeTo(getNode(prop.toId, false));
			// Propagation *to* global space
			else
				for (IAtom pred : prop.preds)
					fromNode.addEdgeTo(getNode(pred.name(), true));

			// Dependencies *from* global space
			Component fromComp = p.comps.get(prop.fromId);
			for (String globalAtom : acActor.getUsedAtoms(fromComp).keySet())
				getNode(globalAtom, true).addEdgeTo(fromNode);
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
		Map<Integer, Set<Node>> layers = new HashMap<>();
		while (!zeroInNodes.isEmpty()) {
			curLayer++;
			Set<Node> newZeroInNodes = new HashSet<>();
			boolean successorsExist = false;
			for (Node n : zeroInNodes) {
				inDegrees.remove(n);
				Set<Node> layerNodes = layers.get(curLayer);
				if (layerNodes == null) layerNodes = new HashSet<>();
				layerNodes.add(n);
				layers.put(curLayer, layerNodes);
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
		}
		//System.out.println(layers);
		//for (Node n : _nodes.values()) n.print();
	}

	Node getNode(String name, boolean isPredicate) {
		// Differentiate predicate names (in the rare case a
		// predicate and a component share the same name)
		if (isPredicate) name = "@" + name;

		Node node = _nodes.get(name);
		if (node == null) {
			node = (isPredicate ? new Node(Node.Type.PRED, name) : new Node(Node.Type.COMP, name));
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
