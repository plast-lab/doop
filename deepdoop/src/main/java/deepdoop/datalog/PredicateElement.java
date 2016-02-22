package deepdoop.datalog;

import java.util.List;

public class PredicateElement implements IElement {
	protected String _name;
	protected List<Object> _parameters;

	public PredicateElement(String name, List<Object> parameters) {
		_name = name;
		_parameters = parameters;
	}

	@Override
	public void normalize() {}

	@Override
	public String toString() {
		StringJoiner joiner = new StringJoiner(", ");
		for (Object p : _parameters) joiner.add(p.toString());
		return _name + "(" + joiner + ")";
	}
}
