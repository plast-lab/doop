package deepdoop.datalog;

import java.util.Arrays;
import java.util.StringJoiner;

public class RefModeElement extends PredicateElement {

	public RefModeElement(String name, String stage, VariableExpr entity, IExpr primitive) {
		super(name, stage, Arrays.asList(entity, primitive));
	}

	@Override
	public String toString() {
		StringJoiner joiner = new StringJoiner(" : ");
		for (IExpr e : _exprs) joiner.add(e.toString());
		return _name + "(" + joiner + ")";
	}
}
