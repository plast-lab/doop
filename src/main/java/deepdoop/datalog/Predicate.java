package deepdoop.datalog;

import java.util.List;

public class Predicate {
	String _name;
	List<String> _types;

	public Predicate(String name, List<String> types) {
		_name = name;
		_types = types;
	}

	@Override
	public String toString() {
		return _name + "/" + _types.size() + " (" + DatalogListenerImpl.join(_types, " x ") + ")";
	}
}
