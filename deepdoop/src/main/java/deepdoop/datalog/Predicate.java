package deepdoop.datalog;

import java.util.List;
import java.util.StringJoiner;

public class Predicate {
	protected String _name;
	protected List<String> _types;

	public Predicate(String name, List<String> types) {
		_name = name;
		_types = types;
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
		return _name + "/" + _types.size() + " (" + joiner + ")";
	}
}
