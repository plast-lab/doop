package deepdoop.actions;

//import deepdoop.actions.AtomCollectorVisitor;
import deepdoop.datalog.*;
import deepdoop.datalog.clause.*;
import deepdoop.datalog.component.*;
import deepdoop.datalog.element.*;
import deepdoop.datalog.element.atom.*;
import deepdoop.datalog.expr.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class FlatteningActor implements IActor<IVisitable> {

	Map<String, Component> _allComps;

	public FlatteningActor(Map<String, Component> allComps) {
		_allComps = allComps;
	}
	public FlatteningActor() {
		this(null);
	}


	@Override
	public Program exit(Program n, Map<IVisitable, IVisitable> m) {
		Program flatP = new Program(n.globalComp, null, n.inits, n.props);
		for (Component c : n.comps.values()) flatP.addComp((Component) m.get(c));
		return flatP;
	}
//		// Check that all used predicates have a declaration/definition
//		Map<String, Set<String>> reversePropagations = new HashMap<>();
//		for (Propagation prop : initP.props) {
//			Set<String> fromSet = reversePropagations.get(prop.toId);
//			if (fromSet == null) fromSet = new HashSet<>();
//			fromSet.add(prop.fromId);
//			reversePropagations.put(prop.toId, fromSet);
//		}
//		initP.accept(acVisitor);
//		Set<String> allDeclAtoms = new HashSet<>(globalAtomNames);
//		Set<String> allUsedAtoms = new HashSet<>();
//		for (Component c : initP.comps.values()) {
//			allDeclAtoms.addAll(acVisitor.getDeclaringAtoms(c).keySet());
//			allUsedAtoms.addAll(acVisitor.getUsedAtoms(c).keySet());
//		}
//		for (String usedPred : allUsedAtoms) {
//			Set<String> potentialDeclPreds = InitVisitor.revert(usedPred, initP.comps.keySet(), reversePropagations);
//			boolean declFound = false;
//			for (String potentialDeclPred : potentialDeclPreds) {
//				if (allDeclAtoms.contains(potentialDeclPred)) {
//					declFound = true;
//					break;
//				}
//			}
//			if (!declFound)
//				throw new DeepDoopException("Predicate `" + usedPred + "` used but not declared");
//		}
//
//		// Final result
//		Program finalP = new Program(new Component(n.globalComp), null, null, null);
//		for (Component c : initP.comps.values()) finalP.globalComp.addAll(c);
//
//		// Handle propagations
//		for (Propagation prop : initP.props) {
//			String fromCompName = initP.inits.get(prop.fromId);
//			Component fromComp  = flatP.comps.get(fromCompName);
//
//			acVisitor = new AtomCollectorVisitor();
//			fromComp.accept(acVisitor);
//			Map<String, IAtom> declAtoms = acVisitor.getDeclaringAtoms(fromComp);
//
//			// Propagate all predicates (*)
//			if (prop.preds.isEmpty())
//				prop.preds.addAll(declAtoms.keySet());
//
//			for (String predName : prop.preds) {
//				IAtom atom = declAtoms.get(predName);
//				if (atom instanceof Directive) continue;
//
//				List<VariableExpr> vars = new ArrayList<>(atom.arity());
//				for (int i = 0 ; i < atom.arity() ; i++) vars.add(new VariableExpr("var" + i));
//
//				// Propagation to global scope
//				if (prop.toId == null && globalAtomNames.contains(atom.name()))
//					throw new DeepDoopException("Reintroducing predicate '" + atom.name() + "' to global space");
//				IElement head = (IAtom) atom.instantiate((prop.toId == null ? null : "@past"), vars).
//					accept(new InitVisitor(prop.toId, globalAtomNames));
//				IElement body = (IAtom) atom.instantiate(null, vars).
//					accept(new InitVisitor(prop.fromId, globalAtomNames));
//				finalP.globalComp.addRule(new Rule(new LogicalElement(head), body));
//			}
//		}
//
//		// Compute dependency graph for initialized components (and global predicates)
//		DependencyGraph g = new DependencyGraph(initP);
//
//		return finalP;

	@Override
	public Constraint exit(Constraint n, Map<IVisitable, IVisitable> m) {
		IElement head = (IElement) m.get(n.head);
		IElement body = (IElement) m.get(n.body);
		return (head == n.head && body == n.body ? n : new Constraint(head, body));
	}
	@Override
	public Rule exit(Rule n, Map<IVisitable, IVisitable> m) {
		LogicalElement head = (LogicalElement) m.get(n.head);
		IElement       body = (IElement) m.get(n.body);
		return (head == n.head && body == n.body ? n : new Rule(head, body));
	}

	@Override
	public Component exit(Component n, Map<IVisitable, IVisitable> m) {
		Component currComp = n;
		Component flatComp = new Component(currComp);
		while (currComp.superComp != null) {
			currComp = _allComps.get(currComp.superComp);
			flatComp.addAll(currComp);
		}
		return flatComp;
	}
	@Override
	public CmdComponent exit(CmdComponent n, Map<IVisitable, IVisitable> m) {
		return n;
	}

	@Override
	public AggregationElement exit(AggregationElement n, Map<IVisitable, IVisitable> m) {
		IElement body = (IElement) m.get(n.body);
		return (body == n.body ? n : new AggregationElement(n.var, n.predicate, body));
	}
	@Override
	public ComparisonElement exit(ComparisonElement n, Map<IVisitable, IVisitable> m) {
		BinaryExpr e = (BinaryExpr) m.get(n.expr);
		return (e == n.expr ? n : new ComparisonElement(e));
	}
	@Override
	public GroupElement exit(GroupElement n, Map<IVisitable, IVisitable> m) {
		IElement e = (IElement) m.get(n.element);
		return (e == n.element ? n : new GroupElement(e));
	}
	@Override
	public LogicalElement exit(LogicalElement n, Map<IVisitable, IVisitable> m) {
		Set<IElement> newElements = new HashSet<>();
		for (IElement e : n.elements) {
			IElement flatE = (IElement) m.get(e);
			if (flatE instanceof LogicalElement && ((LogicalElement)flatE).type == n.type)
				newElements.addAll(((LogicalElement)flatE).elements);
			else
				newElements.add(flatE);
		}
		return new LogicalElement(n.type, newElements);
	}
	@Override
	public NegationElement exit(NegationElement n, Map<IVisitable, IVisitable> m) {
		IElement e = (IElement) m.get(n.element);
		return (e == n.element ? n : new NegationElement(e));
	}
}
