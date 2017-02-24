package org.clyze.deepdoop.actions;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.clyze.deepdoop.datalog.*;
import org.clyze.deepdoop.datalog.clause.*;
import org.clyze.deepdoop.datalog.component.*;
import org.clyze.deepdoop.datalog.element.*;
import org.clyze.deepdoop.datalog.element.atom.*;
import org.clyze.deepdoop.datalog.expr.*;
import org.clyze.deepdoop.system.*;

public class AtomCollectingActor implements IActor<IVisitable> {

	Map<IVisitable, Map<String, IAtom>> _declAtoms;
	Map<IVisitable, Map<String, IAtom>> _usedAtoms;

	public AtomCollectingActor() {
		_declAtoms = new HashMap<>();
		_usedAtoms = new HashMap<>();
	}

	public Map<String, IAtom> getDeclaringAtoms(IVisitable n) {
		Map<String, IAtom> m = _declAtoms.get(n);
		return (m != null ? m : Collections.emptyMap());
	}
	public Map<String, IAtom> getUsedAtoms(IVisitable n) {
		Map<String, IAtom> m = _usedAtoms.get(n);
		return (m != null ? m : Collections.emptyMap());
	}


	@Override
	public Program exit(Program n, Map<IVisitable, IVisitable> m) {
		Map<String, IAtom> declMap = new HashMap<>();
		Map<String, IAtom> usedMap = new HashMap<>();
		declMap.putAll(getDeclaringAtoms(n.globalComp));
		usedMap.putAll(getUsedAtoms(n.globalComp));
		for (Component c : n.comps.values()) {
			declMap.putAll(getDeclaringAtoms(c));
			usedMap.putAll(getUsedAtoms(c));
		}
		_declAtoms.put(n, declMap);
		_usedAtoms.put(n, usedMap);
		return n;
	}

	@Override
	public CmdComponent exit(CmdComponent n, Map<IVisitable, IVisitable> m) {
		Map<String, IAtom> declMap = new HashMap<>();
		for (Declaration d : n.declarations) declMap.putAll(getDeclaringAtoms(d));
		_declAtoms.put(n, declMap);

		Set<String> importPreds = new HashSet<>();
		for (StubAtom p : n.imports) {
			String pName = p.name();
			importPreds.add(pName);
			if (declMap.get(pName) == null)
				ErrorManager.error(ErrorId.CMD_NO_DECL, pName);
		}
		for (String declName : declMap.keySet())
			if (!importPreds.contains(declName))
				ErrorManager.error(ErrorId.CMD_NO_IMPORT, declName);

		Map<String, IAtom> usedMap = new HashMap<>();
		for (StubAtom p : n.exports) usedMap.put(p.name, p);
		_usedAtoms.put(n, usedMap);
		return n;
	}
	@Override
	public Component exit(Component n, Map<IVisitable, IVisitable> m) {
		Map<String, IAtom> declMap = new HashMap<>();
		// The order of the two loops is important! Rules may contain
		// lang:entity declarations that use a StubAtom instead of the actual
		// Atom. Map.putAll overwrites existing keys and we want to keep the
		// one belonging to the actual declaration.
		for (Rule r : n.rules)               declMap.putAll(getDeclaringAtoms(r));
		for (Declaration d : n.declarations) declMap.putAll(getDeclaringAtoms(d));
		_declAtoms.put(n, declMap);

		Map<String, IAtom> usedMap = new HashMap<>();
		for (Declaration d : n.declarations) usedMap.putAll(getUsedAtoms(d));
		for (Constraint c : n.constraints)   usedMap.putAll(getUsedAtoms(c));
		for (Rule r : n.rules)               usedMap.putAll(getUsedAtoms(r));
		_usedAtoms.put(n, usedMap);
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
		usedMap.putAll(getUsedAtoms(n.body));
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
	public Entity exit(Entity n, Map<IVisitable, IVisitable> m) {
		exit((Predicate) n, m);
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
