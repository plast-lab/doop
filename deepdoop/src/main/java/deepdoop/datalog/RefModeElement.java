package deepdoop.datalog;

import java.util.Arrays;

public class RefModeElement extends PredicateElement {

	public RefModeElement(String name, Object firstParameter, Object secondParameter) {
		super(name, Arrays.asList(firstParameter, secondParameter));
	}

	@Override
	public String toString() {
		StringJoiner joiner = new StringJoiner(" : ");
		for (Object p : _parameters) joiner.add(p.toString());
		return _name + "(" + joiner + ")";
	}
}
