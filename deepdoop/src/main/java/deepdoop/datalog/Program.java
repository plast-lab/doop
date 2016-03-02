package deepdoop.datalog;

import java.util.Set;

public class Program {

	Set<Predicate> _predicates;
	Set<Predicate> _specialPredicates;
	Set<Rule> _rules;

	public Program(Set<Predicate> predicates, Set<Predicate> specialPredicates, Set<Rule> rules) {
		_predicates = predicates;
		_specialPredicates = specialPredicates;
		_rules = rules;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		for (Predicate p : _predicates) builder.append(p + "\n");
		for (Predicate p : _specialPredicates) builder.append(p + "\n");
		builder.append("\n");
		for (Rule r : _rules) builder.append(r + "\n");
		return builder.toString();
	}
}
