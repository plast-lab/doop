package deepdoop.datalog;

import java.util.List;

public class Predicate {

	protected String          _name;
	protected List<Predicate> _types;

	public Predicate(String name, List<Predicate> types) {
		_name  = name;
		_types = types;
	}
	public Predicate(String name) {
		this(name, null);
	}

	public Predicate init(String id) {
		return new Predicate(id + ":" + _name, _types);
	}

	public String getName() {
		return _name;
	}

	public void setTypes(List<Predicate> types) {
		_types = types;
	}

	@Override
	public String toString() {
		StringJoiner joiner = new StringJoiner(" x ");
		for (Predicate t : _types) joiner.add(t.getName());
		return _name + "/" + _types.size() + " (" + joiner + ")";
	}
}
