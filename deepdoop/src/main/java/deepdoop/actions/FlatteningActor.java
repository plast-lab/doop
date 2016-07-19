package deepdoop.actions;

import deepdoop.datalog.*;
import deepdoop.datalog.clause.*;
import deepdoop.datalog.component.*;
import deepdoop.datalog.element.*;
import deepdoop.datalog.expr.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
		Program flatP = Program.from(n.globalComp, new HashMap<>(), n.inits, n.props);
		for (Component c : n.comps.values()) flatP.addComponent((Component) m.get(c));
		return flatP;
	}

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
