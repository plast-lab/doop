package deepdoop.datalog;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

class Component implements IInitializable<Component>, IAtomContainer {

	public final String name;
	public final String superComp;
	Set<Declaration>    _declarations;
	Set<Constraint>     _constraints;
	Set<Rule>           _rules;

	public Component(Component other) {
		this.name          = other.name;
		this.superComp     = other.superComp;
		this._declarations = other._declarations;
		this._constraints  = other._constraints;
		this._rules        = other._rules;
	}
	public Component(String name, String superComp, Set<Declaration> declarations, Set<Constraint> constraints, Set<Rule> rules) {
		this.name          = name;
		this.superComp     = superComp;
		this._declarations = declarations;
		this._constraints  = constraints;
		this._rules        = rules;
	}
	public Component(String name, String superComp) {
		this(name, superComp, new HashSet<>(), new HashSet<>(), new HashSet<>());
	}
	public Component(String name) {
		this(name, null, new HashSet<>(), new HashSet<>(), new HashSet<>());
	}
	public Component() {
		this(null, null, new HashSet<>(), new HashSet<>(), new HashSet<>());
	}

	public void TEST() {
		System.out.println(name);
		System.out.println("Atoms: " + getAtoms().keySet().toString());
		System.out.println("DeclAtoms: " + getDeclaringAtoms().keySet().toString());
		System.out.println("InputAtoms: " + getInputAtoms().keySet().toString());
		System.out.println("\n");
	}

	public void addDecl(Declaration d) {
		_declarations.add(d);
	}
	public void addCons(Constraint c) {
		_constraints.add(c);
	}
	public void addRule(Rule r) {
		_rules.add(r);
	}
	public void addAll(Component other) {
		_declarations.addAll(other._declarations);
		_constraints.addAll(other._constraints);
		_rules.addAll(other._rules);
	}

	public Component flatten(Map<String, Component> allComps) {
		Component currComp = this;
		Component flatComp = new Component(currComp);
		while (currComp.superComp != null) {
			currComp = allComps.get(currComp.superComp);
			flatComp.addAll(currComp);
		}
		return flatComp;
	}

	@Override
	public Map<String, IAtom> getAtoms() {
		Map<String, IAtom> map = new HashMap<>();
		for (Declaration d : _declarations) map.putAll(d.getAtoms());
		for (Constraint c : _constraints)   map.putAll(c.getAtoms());
		for (Rule r : _rules)               map.putAll(r.getAtoms());
		return map;
	}

	@Override
	public Map<String, IAtom> getDeclaringAtoms() {
		Map<String, IAtom> map = new HashMap<>();
		for (Declaration d : _declarations) map.putAll(d.getDeclaringAtoms());
		for (Rule r : _rules)               map.putAll(r.getDeclaringAtoms());
		return map;
	}

	@Override
	public Map<String, IAtom> getInputAtoms() {
		Map<String, IAtom> atoms = getAtoms();
		Map<String, IAtom> declaringAtoms = getDeclaringAtoms();
		atoms.keySet().removeAll(declaringAtoms.keySet());
		return atoms;
	}

	@Override
	public Component init(Initializer ini) {
		Component newComp = new Component(ini.id());
		for (Declaration d : _declarations)
			newComp._declarations.add(d.init(ini));
		for (Constraint c : _constraints)
			newComp._constraints.add(c.init(ini));
		for (Rule r : _rules)
			newComp._rules.add(r.init(ini));
		return newComp;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		for (Declaration d : _declarations) builder.append(d + "\n");
		for (Constraint c : _constraints) builder.append(c + "\n");
		for (Rule r : _rules) builder.append(r + "\n");
		return builder.toString();
	}
}
