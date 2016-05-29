package deepdoop.datalog;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

class Component implements IInitializable<Component> {

	public final String           name;
	public final String           superComp;
	private final Set<Declaration> declarations;
	private final Set<Constraint>  constraints;
	private final Set<Rule>        rules;


	public Component(String name, Component other) {
		this.name         = name;
		this.superComp    = null;
		this.declarations = other.declarations;
		this.constraints  = other.constraints;
		this.rules        = other.rules;
	}
	public Component(String name, String superComp, Set<Declaration> declarations, Set<Constraint> constraints, Set<Rule> rules) {
		this.name         = name;
		this.superComp    = superComp;
		this.declarations = declarations;
		this.constraints  = constraints;
		this.rules        = rules;
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


	public String name() {
		return name;
	}

	public void addDecl(Declaration d) {
		declarations.add(d);
	}
	public void addCons(Constraint c) {
		constraints.add(c);
	}
	public void addRule(Rule r) {
		rules.add(r);
	}

	public Component flatten(Map<String, Component> allComps) {
		Component currComp = this;
		Component flatComp = new Component(name, currComp);
		while (currComp.superComp != null) {
			currComp = allComps.get(currComp.superComp);
			flatComp.addAll(currComp);
		}
		return flatComp;
	}

	public Map<String, IAtom> getDeclaringAtoms() {
		Map<String, IAtom> map = new HashMap<>();
		for (Declaration d : declarations)
			map.putAll(d.getDeclaringAtoms());
		for (Rule r : rules)
			map.putAll(r.getDeclaringAtoms());
		return map;
	}

	public void addAll(Component other) {
		declarations.addAll(other.declarations);
		constraints.addAll(other.constraints);
		rules.addAll(other.rules);
	}

	@Override
	public Component init(Initializer ini) {
		Component newComp = new Component(ini.id());
		for (Declaration d : declarations)
			newComp.declarations.add(d.init(ini));
		for (Constraint c : constraints)
			newComp.constraints.add(c.init(ini));
		for (Rule r : rules)
			newComp.rules.add(r.init(ini));
		return newComp;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		for (Declaration d : declarations) builder.append(d + "\n");
		for (Constraint c : constraints) builder.append(c + "\n");
		for (Rule r : rules) builder.append(r + "\n");
		return builder.toString();
	}
}
