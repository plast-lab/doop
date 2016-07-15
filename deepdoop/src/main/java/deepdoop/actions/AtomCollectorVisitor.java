package deepdoop.actions;

import deepdoop.datalog.*;
import deepdoop.datalog.clause.*;
import deepdoop.datalog.component.*;
import deepdoop.datalog.element.*;
import deepdoop.datalog.element.atom.*;
import deepdoop.datalog.expr.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AtomCollectorVisitor implements IVisitor {

	Map<IVisitable, Map<String, IAtom>> _declAtoms;
	Map<IVisitable, Map<String, IAtom>> _usedAtoms;
	Map<IVisitable, Map<String, IAtom>> _allAtoms;

	public AtomCollectorVisitor() {
		_declAtoms = new HashMap<>();
		_usedAtoms = new HashMap<>();
		_allAtoms  = new HashMap<>();
	}

	public Map<String, IAtom> getDeclaringAtoms(IVisitable n) {
		Map<String, IAtom> m = _declAtoms.get(n);
		return (m != null ? m : Collections.emptyMap());
	}
	public Map<String, IAtom> getUsedAtoms(IVisitable n) {
		Map<String, IAtom> m = _usedAtoms.get(n);
		return (m != null ? m : Collections.emptyMap());
	}
	public Map<String, IAtom> getAtoms(IVisitable n) {
		Map<String, IAtom> allMap = _allAtoms.get(n);
		if (allMap != null) return allMap;

		Map<String, IAtom> declMap = getDeclaringAtoms(n);
		Map<String, IAtom> usedMap = getUsedAtoms(n);
		if (declMap.isEmpty() && usedMap.isEmpty()) return Collections.emptyMap();

		allMap = new HashMap<>(declMap);
		allMap.putAll(usedMap);
		_allAtoms.put(n, allMap);
		return allMap;
	}


	@Override
	public Program exit(Program n, Map<IVisitable, IVisitable> m) {
		// TODO
		return n;
	}

	@Override
	public Constraint exit(Constraint n, Map<IVisitable, IVisitable> m) {
		Map<String, IAtom> usedMap = new HashMap<>();
		usedMap.putAll(getUsedAtoms(n.head));
		usedMap.putAll(getUsedAtoms(n.body));
		_usedAtoms.put(n, usedMap);
		return n;
	}
	@Override
	public Declaration exit(Declaration n, Map<IVisitable, IVisitable> m) {
		Map<String, IAtom> declMap = Collections.singletonMap(n.atom.name(), n.atom);
		_declAtoms.put(n, declMap);
		Map<String, IAtom> usedMap = new HashMap<>();
		for (IAtom t : n.types) usedMap.putAll(getUsedAtoms(t));
		_usedAtoms.put(n, usedMap);
		return n;
	}
	@Override
	public RefModeDeclaration exit(RefModeDeclaration n, Map<IVisitable, IVisitable> m) {
		Map<String, IAtom> declMap = new HashMap<>();
		declMap.put(n.atom.name(), n.atom);
		declMap.put(n.types.get(0).name(), n.types.get(0));
		_declAtoms.put(n, declMap);
		return n;
	}
	@Override
	public Rule exit(Rule n, Map<IVisitable, IVisitable> m) {
		// Atoms used in the head, are declared in the rule
		Map<String, IAtom> declMap = new HashMap<>();
		declMap.putAll(getUsedAtoms(n.head));
		_declAtoms.put(n, declMap);

		Map<String, IAtom> usedMap = new HashMap<>();
		usedMap.putAll(getUsedAtoms(n.head));
		usedMap.putAll(getUsedAtoms(n.body));
		_usedAtoms.put(n, usedMap);
		return n;
	}

	@Override
	public CmdComponent exit(CmdComponent n, Map<IVisitable, IVisitable> m) {
		// TODO check that declarations and imports match
		Map<String, IAtom> declMap = new HashMap<>();
		for (Declaration d : n.declarations) declMap.putAll(getDeclaringAtoms(d));
		_declAtoms.put(n, declMap);

		Map<String, IAtom> usedMap = new HashMap<>();
		for (StubAtom p : n.exports) usedMap.put(p.name, p);
		_usedAtoms.put(n, usedMap);
		return n;
	}
	@Override
	public Component exit(Component n, Map<IVisitable, IVisitable> m) {
		Map<String, IAtom> declMap = new HashMap<>();
		for (Declaration d : n.declarations) declMap.putAll(getDeclaringAtoms(d));
		for (Rule r : n.rules)               declMap.putAll(getDeclaringAtoms(r));
		_declAtoms.put(n, declMap);

		Map<String, IAtom> usedMap = new HashMap<>();
		for (Declaration d : n.declarations) usedMap.putAll(getUsedAtoms(d));
		for (Constraint c : n.constraints)   usedMap.putAll(getUsedAtoms(c));
		for (Rule r : n.rules)               usedMap.putAll(getUsedAtoms(r));
		_usedAtoms.put(n, usedMap);
		return n;
	}

	@Override
	public AggregationElement exit(AggregationElement n, Map<IVisitable, IVisitable> m) {
		_usedAtoms.put(n, getUsedAtoms(n.body));
		return n;
	}
	@Override
	public ComparisonElement exit(ComparisonElement n, Map<IVisitable, IVisitable> m) {
		_usedAtoms.put(n, getUsedAtoms(n.expr));
		return n;
	}
	@Override
	public GroupElement exit(GroupElement n, Map<IVisitable, IVisitable> m) {
		_usedAtoms.put(n, getUsedAtoms(n.element));
		return n;
	}
	@Override
	public LogicalElement exit(LogicalElement n, Map<IVisitable, IVisitable> m) {
		Map<String, IAtom> usedMap = new HashMap<>();
		for (IElement e : n.elements) usedMap.putAll(getUsedAtoms(e));
		_usedAtoms.put(n, usedMap);
		return n;
	}
	@Override
	public NegationElement exit(NegationElement n, Map<IVisitable, IVisitable> m) {
		_usedAtoms.put(n, getUsedAtoms(n.element));
		return n;
	}

	@Override
	public Directive exit(Directive n, Map<IVisitable, IVisitable> m) {
		if (n.isPredicate) {
			Map<String, IAtom> usedMap = Collections.singletonMap(n.backtick.name(), n.backtick);
			_usedAtoms.put(n, usedMap);
		}
		return n;
	}
	@Override
	public Functional exit(Functional n, Map<IVisitable, IVisitable> m) {
		Map<String, IAtom> usedMap = new HashMap<>();
		usedMap.put(n.name(), n);
		for (IExpr e : n.keyExprs) usedMap.putAll(getUsedAtoms(e));
		if (n.valueExpr != null) usedMap.putAll(getUsedAtoms(n.valueExpr));
		_usedAtoms.put(n, usedMap);
		return n;
	}
	@Override
	public Predicate exit(Predicate n, Map<IVisitable, IVisitable> m) {
		Map<String, IAtom> usedMap = new HashMap<>();
		usedMap.put(n.name(), n);
		for (IExpr e : n.exprs) usedMap.putAll(getUsedAtoms(e));
		_usedAtoms.put(n, usedMap);
		return n;
	}
	@Override
	public RefMode exit(RefMode n, Map<IVisitable, IVisitable> m) {
		Map<String, IAtom> usedMap = new HashMap<>();
		usedMap.put(n.name(), n);
		usedMap.putAll(getUsedAtoms(n.valueExpr));
		_usedAtoms.put(n, usedMap);
		return n;
	}

	@Override
	public BinaryExpr exit(BinaryExpr n, Map<IVisitable, IVisitable> m) {
		Map<String, IAtom> usedMap = new HashMap<>();
		usedMap.putAll(getUsedAtoms(n.left));
		usedMap.putAll(getUsedAtoms(n.right));
		_usedAtoms.put(n, usedMap);
		return n;
	}
	@Override
	public FunctionalHeadExpr exit(FunctionalHeadExpr n, Map<IVisitable, IVisitable> m) {
		_usedAtoms.put(n, getUsedAtoms(n.functional));
		return n;
	}
	@Override
	public GroupExpr exit(GroupExpr n, Map<IVisitable, IVisitable> m) {
		_usedAtoms.put(n, getUsedAtoms(n.expr));
		return n;
	}
}
