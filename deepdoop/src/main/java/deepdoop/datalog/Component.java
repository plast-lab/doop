package deepdoop.datalog;

import java.util.HashSet;
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

	@Override
	public String toString() {
		return null;
	}
}
