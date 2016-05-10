package deepdoop.datalog;

import java.util.List;

public class Predicate {
	protected String _name;
	// Computed in bytes needed
	protected int _capacity;
	protected List<String> _types;

	public Predicate(String name, String capacity, List<String> types) {
		_name = name;
		_types = types;
		_capacity = capacity == null ? 0 : Integer.parseInt(capacity.substring(1).substring(0, capacity.length()-2));
	}
	public Predicate(String name, String capacity) {
		this(name, capacity, null);
	}
	public Predicate(String name) {
		this(name, null, null);
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
		return _name + "[" + _capacity + "]/" + _types.size() + " (" + joiner + ")";
	}
}
