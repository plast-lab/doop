package deepdoop.datalog;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

public class Predicate {
	protected String _name;
	List<String> _types;
	//List<Rule> _rules;

	public final boolean isFunctional;

	public Predicate(String name, boolean isFunctional) {
		this(name, new ArrayList<>(), isFunctional);
	}
	public Predicate(String name, List<String> types) {
		this(name, types, false);
	}
	public Predicate(String name, List<String> types, boolean isFunctional) {
		_name = name;
		_types = types;
		this.isFunctional = isFunctional;
	}

	public String getName() {
		return _name;
	}

	public void setTypes(List<String> types) {
		_types = types;
	}

	@Override
	public String toString() {
		StringJoiner joiner = new StringJoiner(" x ");
		for (String s : _types) joiner.add(s);
		String t = joiner.toString();
		if (isFunctional) {
			int pos = t.lastIndexOf(" x ");
			t = new StringBuilder(t).replace(pos, pos+3, " -> ").toString();
		}
		return _name + "/" + _types.size() + " (" + t + ")";
	}
}
