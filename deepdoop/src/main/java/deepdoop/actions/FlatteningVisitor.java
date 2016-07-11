package deepdoop.actions;

import deepdoop.actions.AtomCollectorVisitor;
import deepdoop.datalog.*;
import deepdoop.datalog.clause.*;
import deepdoop.datalog.component.*;
import deepdoop.datalog.element.*;
import deepdoop.datalog.element.atom.*;
import deepdoop.datalog.expr.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class FlatteningVisitor implements IVisitor {

	Map<String, Component> _allComps;

	public FlatteningVisitor(Map<String, Component> allComps) {
		_allComps = allComps;
	}
	public FlatteningVisitor() {
		this(null);
	}


	@Override
	public Program exit(Program n, Map<IVisitable, IVisitable> m) {
		AtomCollectorVisitor acVisitor = new AtomCollectorVisitor();
		n.globalComp.accept(acVisitor);
		Set<String> globalAtomNames = acVisitor.getDeclaringAtoms(n.globalComp).keySet();

		// Flatten components
		Program flatP = new Program();
		for (Component c : n.comps.values()) flatP.addComp((Component) m.get(c));

		// Initialize components
		Program initP = new Program();
		for (Entry<String, String> entry : n.inits.entrySet()) {
			String initName = entry.getKey();
			String compName = entry.getValue();
			Component c     = flatP.comps.get(compName);
			InitVisitor initVisitor = new InitVisitor(initName, globalAtomNames);
			initP.addComp((Component) c.accept(initVisitor));
		}

		Program finalP = new Program();
		finalP.setGlobalComp(new Component(n.globalComp));
		for (Component c : initP.comps.values()) finalP.globalComp.addAll(c);

		// Handle propagations
		for (Propagation prop : n.props) {
			String fromCompName = n.inits.get(prop.fromId);
			String toCompName   = n.inits.get(prop.toId);
			Component fromComp  = initP.comps.get(fromCompName);
			Component toComp    = initP.comps.get(toCompName);

			acVisitor = new AtomCollectorVisitor();
			fromComp.accept(acVisitor);
			Map<String, IAtom> declAtoms = acVisitor.getDeclaringAtoms(fromComp);

			// Propagate all predicates (*)
			if (prop.preds.isEmpty())
				prop.preds.addAll(declAtoms.keySet());

			for (String predName : prop.preds) {
				IAtom atom = declAtoms.get(predName);
				if (atom instanceof Directive) continue;

				List<VariableExpr> vars = new ArrayList<>(atom.arity());
				for (int i = 0 ; i < atom.arity() ; i++) vars.add(new VariableExpr("var" + i));

				// Propagation to global scope
				if (prop.toId == null && globalAtomNames.contains(atom.name()))
					throw new DeepDoopException("Reintroducing predicate '" + atom.name() + "' to global space");
				IElement head = (IAtom) atom.instantiate((prop.toId == null ? null : "@past"), vars).
					accept(new InitVisitor(prop.toId, globalAtomNames));
				IElement body = (IAtom) atom.instantiate(null, vars).
					accept(new InitVisitor(prop.fromId, globalAtomNames));
				finalP.globalComp.addRule(new Rule(new LogicalElement(head), body));
			}
		}

		return finalP;
	}


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
