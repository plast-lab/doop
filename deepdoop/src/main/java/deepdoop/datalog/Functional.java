package deepdoop.datalog;

import java.util.List;

public class Functional extends Predicate {
	Predicate _valueType;

	public Functional(String name, List<Predicate> keyTypes, Predicate valueType) {
		super(name, keyTypes);
		_valueType = valueType;
	}
	public Functional(String name) {
		super(name);
	}

	@Override
	public Predicate init(String id) {
		return new Functional(id + ":" + _name, _types, _valueType);
	}

	@Override
	public void setTypes(List<Predicate> types) {
		_valueType = types.remove(types.size() - 1);
		_types = types;
	}

	@Override
	public String toString() {
		StringJoiner joiner = new StringJoiner(" x ");
		for (Predicate t : _types) joiner.add(t.getName());
		return _name + "/" + _types.size() + " (" + joiner + " -> " + _valueType.getName() + ")";
	}
}
