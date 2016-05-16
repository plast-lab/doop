package deepdoop.datalog;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

class Component {

	static final String GLOBAL_COMP = DatalogParser.VOCABULARY.getLiteralName(DatalogParser.GLOBAL);

	public final String         name;
	public final String         superComp;
	public final Set<IAtom> atoms;
	public final Set<IAtom> types;
	public final Set<Rule>      rules;

	public Component(String name, String superComp, Set<IAtom> atoms, Set<IAtom> types, Set<Rule> rules) {
		this.name      = name;
		this.superComp = superComp;
		this.atoms     = atoms;
		this.types     = types;
		this.rules     = rules;

		//infos = new HashMap<>();
		//for (IAtom a : atoms) {
		//}
	}
	public Component(String name, String superComp) {
		this(name, superComp, new HashSet<>(), new HashSet<>(), new HashSet<>());
	}

	public Component init(String id,  Map<String, Component> allComps) {
		Set<IAtom> allAtoms = new HashSet<>(atoms);
		Set<IAtom> allTypes = new HashSet<>(types);
		Set<Rule>  allRules = new HashSet<>(rules);
		Component currComp = this;
		while (currComp.superComp != null) {
			currComp = allComps.get(currComp.superComp);
			allAtoms.addAll(currComp.atoms);
			allTypes.addAll(currComp.types);
			allRules.addAll(currComp.rules);
		}

		Set<IAtom> newAtoms = new HashSet<>();
		Set<IAtom> newTypes = new HashSet<>();
		Set<Rule>  newRules = new HashSet<>();
		for (IAtom a : allAtoms) {
			newAtoms.add(a.init(id));
		}
		for (IAtom a : allTypes) {
			newTypes.add(a.init(id));
		}
		for (Rule r : allRules) {
			newRules.add(r.init(id));
		}
		return new Component(id, null, newAtoms, newTypes, newRules);
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		for (IAtom a : atoms) builder.append(a + "\n");
		for (IAtom a : types) builder.append(a + "\n");
		builder.append("\n");
		for (Rule r : rules) builder.append(r + "\n");
		return builder.toString();
	}
}
