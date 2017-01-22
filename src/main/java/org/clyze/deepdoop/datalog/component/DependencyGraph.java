package org.clyze.deepdoop.datalog.component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.clyze.deepdoop.actions.*;
import org.clyze.deepdoop.datalog.Program;
import org.clyze.deepdoop.datalog.element.atom.IAtom;
import org.clyze.deepdoop.system.*;

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

		p.props.forEach(prop -> {
			Component fromComp = p.comps.get(prop.fromId);
			Component toComp   = p.comps.get(prop.toId);

			Node fromNode = getNode(fromComp);

			// Propagate to another component
			if (toComp != null)
				fromNode.addEdgeTo(getNode(toComp));
			// Propagate to global space
			else
				prop.preds.forEach(pred -> {
					IAtom newPred = (IAtom) pred.accept(new InitVisitingActor());
					//IAtom newPred = (IAtom) pred.accept(new InitVisitingActor(prop.fromId, null, new HashSet<>()));
					fromNode.addEdgeTo(getNode(newPred.name()));
					handledGlobalAtoms.add(newPred.name());
				});

			// Dependencies from global space
			Set<String> fromGlobal  = acActor.getUsedAtoms(fromComp).keySet();
			fromGlobal.retainAll(globalAtoms);

			fromGlobal.forEach(globalAtomName -> getNode(globalAtomName).addEdgeTo(fromNode));
			handledGlobalAtoms.addAll(fromGlobal);
		});

		// Topological sort
		Map<Node, Integer> inDegrees = new HashMap<>();
		Set<Node> zeroInNodes = new HashSet<>();
		for (Node n : _nodes.values()) {
			int inDegree = n.inEdges.size();
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
				for (Node succ : n.outEdges) {
					successorsExist = true;
					int newInDegree = inDegrees.get(succ) - 1;
					inDegrees.put(succ, newInDegree);
					if (newInDegree == 0) newZeroInNodes.add(succ);
				}
			}
			if (newZeroInNodes.isEmpty() && successorsExist)
				ErrorManager.error(ErrorId.DEP_CYCLE);
			zeroInNodes = newZeroInNodes;
			curLayer++;
		}

		globalAtoms.removeAll(handledGlobalAtoms);
		Set<Node> lastGlobalNodes = new HashSet<>();
		globalAtoms.forEach(globalAtom -> lastGlobalNodes.add(getNode(globalAtom)));
		_layers.add(lastGlobalNodes);
	}

	public List<Set<Node>> getLayers() {
		return _layers;
	}

	Node getNode(String name) {
		String key = "<" + name + ">";
		Node node = _nodes.get(key);
		if (node == null) {
			node = new PredNode(name);
			_nodes.put(key, node);
		}
		return node;
	}
	Node getNode(Component comp) {
		String key = comp.name;
		Node node = _nodes.get(key);
		if (node == null) {
			node = (comp instanceof CmdComponent ? new CmdNode(comp.name) : new CompNode(comp.name));
			_nodes.put(key, node);
		}
		return node;
	}


	public abstract static class Node {

		public final String    name;
		public final Set<Node> inEdges;
		public final Set<Node> outEdges;

		Node(String name) {
			this.name     = name;
			this.inEdges  = new HashSet<>();
			this.outEdges = new HashSet<>();
		}

		void addEdgeTo(Node toNode) {
			outEdges.add(toNode);
			toNode.inEdges.add(this);
		}

		void print() {
			outEdges.forEach(n -> System.out.println(this + " -----> " + n));
		}
	}

	public static class PredNode extends Node {
		public PredNode(String name) {
			super(name);
		}
		@Override
		public String toString() { return name; }
	}
	public static class CompNode extends Node {
		public CompNode(String name) {
			super(name);
		}
		@Override
		public String toString() { return "<" + name + ">"; }
	}
	public static class CmdNode extends Node {
		public CmdNode(String name) {
			super(name);
		}
		@Override
		public String toString() { return "{" + name + "}"; }
	}
}
