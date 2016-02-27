package deepdoop.datalog;

import java.util.Arrays;

public class RefModeElement extends PredicateElement {

	public RefModeElement(String name, VariableExpr entity, IExpr primitive) {
		super(name, Arrays.asList(entity, primitive));
	}

	@Override
	public String toString() {
		StringJoiner joiner = new StringJoiner(" : ");
		for (IExpr p : _params) joiner.add(p.toString());
		return _name + "(" + joiner + ")";
	}
}
