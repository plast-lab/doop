package deepdoop.datalog;

import java.util.List;

public class FunctionalHeadElement extends FunctionalElement {

	public FunctionalHeadElement(String name, List<Object> keyParameters) {
		super(name, keyParameters, null);
	}

	@Override
	public String toString() {
		StringJoiner joiner = new StringJoiner(", ");
		for (Object p : _parameters) joiner.add(p.toString());
		return _name + "[" + joiner + "]";
	}
}
