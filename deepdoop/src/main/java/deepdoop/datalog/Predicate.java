package deepdoop.datalog;

import java.util.List;
import java.util.StringJoiner;

public class Predicate {
	String _name;
	List<String> _types;

	public final boolean isFunctional;


	public Predicate(String name, List<String> types) {
		this(name, types, false);
	}
	public Predicate(String name, List<String> types, boolean isFunctional) {
		_name = name;
		_types = types;
		this.isFunctional = isFunctional;
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
