package deepdoop.datalog;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

class Component implements IInitializable<Component> {

	public final String     name;
	public final String     superComp;
	public final Set<IAtom> atoms;
	public final Set<Rule>  rules;


	public Component(String name, String superComp, Set<IAtom> atoms, Set<Rule> rules) {
		this.name      = name;
		this.superComp = superComp;
		this.atoms     = atoms;
		this.rules     = rules;
	}
	public Component(String name, String superComp) {
		this(name, superComp, new HashSet<>(), new HashSet<>());
	}
	public Component(String name) {
		this(name, null, new HashSet<>(), new HashSet<>());
	}
	public Component() {
		this(null, null, new HashSet<>(), new HashSet<>());
	}


	@Override
	public Component init(Initializer ini) {
		Set<IAtom> newAtoms = new HashSet<>();
		Set<Rule>  newRules = new HashSet<>();
		for (IAtom a : atoms) {
			newAtoms.add(a.init(ini));
		}
		for (Rule r : rules) {
			newRules.add(r.init(ini));
		}
		return new Component(ini.id(), null, newAtoms, newRules);
	}

	public Component flatten(Map<String, Component> allComps) {
		Set<IAtom> allAtoms = new HashSet<>(atoms);
		Set<Rule>  allRules = new HashSet<>(rules);
		Component currComp = this;
		while (currComp.superComp != null) {
			currComp = allComps.get(currComp.superComp);
			allAtoms.addAll(currComp.atoms);
			allRules.addAll(currComp.rules);
		}
		return new Component(name, null, allAtoms, allRules);
	}

	public Map<String, IAtom> getAtoms() {
		Map<String, IAtom> map = new HashMap<>();
		for (IAtom a : atoms) {
			map.put(a.name(), a);
		}
		for (Rule r : rules) {
			map.putAll(r.getAtoms());
		}
		return map;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		for (IAtom a : atoms) builder.append(a + "\n");
		builder.append("\n");
		for (Rule r : rules) builder.append(r + "\n");
		return builder.toString();
	}
}
