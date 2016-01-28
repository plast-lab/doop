package deepdoop.datalog;

import java.util.List;
import java.util.StringJoiner;

public class FunctionalInstance extends PredicateInstance {
	Object _valueParameter;

	public FunctionalInstance(String name, List<Object> keyParameters, Object valueParameter) {
		super(name, keyParameters);
		_valueParameter = valueParameter;
	}

	@Override
	public void normalize() {}

	@Override
	public String toString() {
		StringJoiner joiner = new StringJoiner(", ");
		for (Object p : _parameters) joiner.add(p.toString());
		return _name + "[" + joiner + "] = " + _valueParameter;
	}
}
