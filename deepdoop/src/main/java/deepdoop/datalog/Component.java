package deepdoop.datalog;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

class Component {

	static final String GLOBAL_COMP = DatalogParser.VOCABULARY.getLiteralName(DatalogParser.GLOBAL);

	public final String         name;
	public final String         superComp;
	public final Set<Predicate> preds;
	public final Set<Predicate> types;
	public final Set<Rule>      rules;

	public Component(String name, String superComp, Set<Predicate> preds, Set<Predicate> types, Set<Rule> rules) {
		this.name      = name;
		this.superComp = superComp;
		this.preds     = preds;
		this.types     = types;
		this.rules     = rules;
	}
	public Component(String name, String superComp) {
		this(name, superComp, new HashSet<Predicate>(), new HashSet<Predicate>(), new HashSet<Rule>());
	}

	public Component init(String id,  Map<String, Component> allComps) {
		Set<Predicate> allPreds = new HashSet<>(preds);
		Set<Predicate> allTypes = new HashSet<>(types);
		Set<Rule>      allRules = new HashSet<>(rules);
		Component currComp = this;
		while (currComp.superComp != null) {
			currComp = allComps.get(currComp.superComp);
			allPreds.addAll(currComp.preds);
			allTypes.addAll(currComp.types);
			allRules.addAll(currComp.rules);
		}

		Set<Predicate> newPreds = new HashSet<>();
		Set<Predicate> newTypes = new HashSet<>();
		Set<Rule>      newRules = new HashSet<>();
		for (Predicate p : allPreds) {
			newPreds.add(p.init(id));
		}
		for (Predicate p : allTypes) {
			newTypes.add(p.init(id));
		}
		for (Rule r : allRules) {
			newRules.add(r.init(id));
		}
		return new Component(id, null, newPreds, newTypes, newRules);
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		for (Predicate p : preds) builder.append(p + "\n");
		for (Predicate p : types) builder.append(p + "\n");
		builder.append("\n");
		for (Rule r : rules) builder.append(r + "\n");
		return builder.toString();
	}
}
