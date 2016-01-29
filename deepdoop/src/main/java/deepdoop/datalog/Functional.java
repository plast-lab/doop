package deepdoop.datalog;

import java.util.List;
import java.util.StringJoiner;

public class Functional extends Predicate {
	String _valueType;

	public Functional(String name, List<String> keyTypes, String valueType) {
		super(name, keyTypes);
		_valueType = valueType;
	}

	@Override
	public String toString() {
		StringJoiner joiner = new StringJoiner(" x ");
		for (String s : _types) joiner.add(s);
		return _name + "/" + _types.size() + " (" + joiner + " -> " + _valueType + ")";
	}
}
