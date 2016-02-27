package deepdoop.datalog;

import java.util.List;

public class FunctionalElement extends PredicateElement {
	IExpr _valueParam;

	public FunctionalElement(String name, List<IExpr> keyParams, IExpr valueParam) {
		super(name, keyParams);
		_valueParam = valueParam;
	}

	@Override
	public String toString() {
		StringJoiner joiner = new StringJoiner(", ");
		for (IExpr p : _params) joiner.add(p.toString());
		return _name + "[" + joiner + "] = " + _valueParam;
	}
}
