package deepdoop.actions;

import deepdoop.datalog.clause.*;
import deepdoop.datalog.component.*;
import deepdoop.datalog.element.*;
import deepdoop.datalog.element.atom.*;
import deepdoop.datalog.expr.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class InitVisitor implements IVisitor {

	String      _id;
	Set<String> _globalAtoms;

	public InitVisitor(String id, Set<String> globalAtoms) {
		_id          = id;
		_globalAtoms = globalAtoms;
	}

	String name(String name, String stage) {
		if (_globalAtoms.contains(name) || _id == null) {
			assert !"@past".equals(stage);
			return name;
		}
		else
			return _id + ":" + name + ("@past".equals(stage) ? ":past" : "");
	}
	String name(String name) {
		return name(name, null);
	}
	String stage(String stage) {
		return ("@past".equals(stage) ? null : stage);
	}


	@Override
	public Constraint exit(Constraint n, Map<IVisitable, IVisitable> m) {
		return new Constraint((IElement) m.get(n.head), (IElement) m.get(n.body));
	}
	@Override
	public Declaration exit(Declaration n, Map<IVisitable, IVisitable> m) {
		Set<IAtom> newTypes = new HashSet<>();
		for (IAtom t : n.types) newTypes.add((IAtom) m.get(t));
		return new Declaration((IAtom) m.get(n.atom), newTypes);
	}
	@Override
	public RefModeDeclaration exit(RefModeDeclaration n, Map<IVisitable, IVisitable> m) {
		return new RefModeDeclaration((RefMode) m.get(n.atom), (Predicate) m.get(n.types.get(0)), (Primitive) m.get(n.types.get(1)));
	}
	@Override
	public Rule exit(Rule n, Map<IVisitable, IVisitable> m) {
		return new Rule((LogicalElement) m.get(n.head), (IElement) m.get(n.body));
	}

	@Override
	public CmdComponent exit(CmdComponent n, Map<IVisitable, IVisitable> m) {
		Set<Declaration> newDeclarations = new HashSet<>();
		for (Declaration d : n.declarations) newDeclarations.add((Declaration) m.get(d));
		Set<String> newImports = new HashSet<>();
		for (String pred : n.imports) newImports.add(name(pred));
		Set<String> newExports = new HashSet<>();
		for (String pred : n.exports) newExports.add(name(pred));
		return new CmdComponent(_id, newDeclarations, n.eval, n.dir, newImports, newExports);
	}
	@Override
	public Component exit(Component n, Map<IVisitable, IVisitable> m) {
		Component newComp = new Component(_id);
		for (Declaration d : n.declarations) newComp.declarations.add((Declaration) m.get(d));
		for (Constraint c : n.constraints)   newComp.constraints.add((Constraint) m.get(c));
		for (Rule r : n.rules)               newComp.rules.add((Rule) m.get(r));
		return newComp;
	}

	@Override
	public AggregationElement exit(AggregationElement n, Map<IVisitable, IVisitable> m) {
		return new AggregationElement((VariableExpr) m.get(n.var), (Predicate) m.get(n.predicate), (IElement) m.get(n.body));
	}
	@Override
	public ComparisonElement exit(ComparisonElement n, Map<IVisitable, IVisitable> m) {
		return new ComparisonElement((BinaryExpr) m.get(n.expr));
	}
	@Override
	public GroupElement exit(GroupElement n, Map<IVisitable, IVisitable> m) {
		return new GroupElement((IElement) m.get(n.element));
	}
	@Override
	public LogicalElement exit(LogicalElement n, Map<IVisitable, IVisitable> m) {
		Set<IElement> newElements = new HashSet<>();
		for (IElement e : n.elements) newElements.add((IElement) m.get(e));
		return new LogicalElement(n.type, newElements);
	}
	@Override
	public NegationElement exit(NegationElement n, Map<IVisitable, IVisitable> m) {
		return new NegationElement((IElement) m.get(n.element));
	}

	@Override
	public Directive exit(Directive n, Map<IVisitable, IVisitable> m) {
		if (n.backtick == null)
			return n;
		else if (n.isPredicate)
			return new Directive(n.name, name(n.backtick));
		else
			return new Directive(n.name, name(n.backtick), n.constant);
	}
	@Override
	public Functional exit(Functional n, Map<IVisitable, IVisitable> m) {
		List<IExpr> newKeyExprs = new ArrayList<>();
		for (IExpr e : n.keyExprs) newKeyExprs.add((IExpr) m.get(e));
		return new Functional(name(n.name, n.stage), stage(n.stage), newKeyExprs, (IExpr) m.get(n.valueExpr));
	}
	@Override
	public Predicate exit(Predicate n, Map<IVisitable, IVisitable> m) {
		List<IExpr> newExprs = new ArrayList<>();
		for (IExpr e : n.exprs) newExprs.add((IExpr) m.get(e));
		return new Predicate(name(n.name, n.stage), stage(n.stage), newExprs);
	}
	@Override
	public Primitive exit(Primitive n, Map<IVisitable, IVisitable> m) {
		return n;
	}
	@Override
	public RefMode exit(RefMode n, Map<IVisitable, IVisitable> m) {
		return new RefMode(name(n.name), stage(n.stage), (VariableExpr) m.get(n.entityVar), (IExpr) m.get(n.valueExpr));
	}


	@Override
	public BinaryExpr exit(BinaryExpr n, Map<IVisitable, IVisitable> m) {
		return new BinaryExpr((IExpr) m.get(n.left), n.op, (IExpr) m.get(n.right));
	}
	@Override
	public ConstantExpr exit(ConstantExpr n, Map<IVisitable, IVisitable> m) {
		return n;
	}
	@Override
	public FunctionalHeadExpr exit(FunctionalHeadExpr n, Map<IVisitable, IVisitable> m) {
		return new FunctionalHeadExpr((Functional) m.get(n.functional));
	}
	@Override
	public GroupExpr exit(GroupExpr n, Map<IVisitable, IVisitable> m) {
		return new GroupExpr((IExpr) m.get(n.expr));
	}
	@Override
	public VariableExpr exit(VariableExpr n, Map<IVisitable, IVisitable> m) {
		return n;
	}


	// S1:P0 -> P0
	// S3:P1:past -> S1:P1, S2:P1 when S1 and S2 propagate P1 to S3
	// A list is needed for cases like the second one
	public static Set<String> revert(String name, Set<String> initIds, Map<String, Set<String>> reversePropsMap) {
		int i = name.indexOf(':');
		if (i == -1) return Collections.singleton(name);

		String id = name.substring(0, i);
		String newName = name.substring(i+1, name.length());

		if (newName.endsWith(":past") && reversePropsMap.get(id) != null) {
			newName = newName.substring(0, newName.lastIndexOf(":past"));
			Set<String> fromSet = reversePropsMap.get(id);
			Set<String> result = new HashSet<>();
			for (String fromId : fromSet) {
				result.add(fromId + ":" + newName);
			}
			return result;
		}
		else
			return Collections.singleton(newName);
	}
}
