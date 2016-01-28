package deepdoop.datalog;

import java.util.Arrays;
import java.util.StringJoiner;

public class RefModeInstance extends PredicateInstance {

	public RefModeInstance(String name, Object firstParameter, Object secondParameter) {
		super(name, Arrays.asList(firstParameter, secondParameter));
	}

	@Override
	public void normalize() {}

	@Override
	public String toString() {
		StringJoiner joiner = new StringJoiner(" : ");
		for (Object p : _parameters) joiner.add(p.toString());
		return _name + "(" + joiner + ")";
	}
}
