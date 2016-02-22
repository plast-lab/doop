package deepdoop.datalog;

import java.util.List;

public class FunctionalElement extends PredicateElement {
	Object _valueParameter;

	public FunctionalElement(String name, List<Object> keyParameters, Object valueParameter) {
		super(name, keyParameters);
		_valueParameter = valueParameter;
	}

	@Override
	public String toString() {
		StringJoiner joiner = new StringJoiner(", ");
		for (Object p : _parameters) joiner.add(p.toString());
		return _name + "[" + joiner + "] = " + _valueParameter;
	}
}
