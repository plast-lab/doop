package deepdoop.datalog;

import java.util.List;

public class PredicateElement implements IElement {

	protected String _name;
	protected String _stage;
	protected List<IExpr> _exprs;

	public PredicateElement(String name, String stage, List<IExpr> exprs) {
		_name = name;
		_stage = stage;
		_exprs = exprs;
	}

	@Override
	public void normalize() {}

	@Override
	public String toString() {
		StringJoiner joiner = new StringJoiner(", ");
		for (IExpr e : _exprs) joiner.add(e.toString());
		return _name + "(" + joiner + ")";
	}
}
