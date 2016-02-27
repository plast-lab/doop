package deepdoop.datalog;

import java.util.List;

public class PredicateElement implements IElement {
	protected String _name;
	protected List<IExpr> _params;

	public PredicateElement(String name, List<IExpr> params) {
		_name = name;
		_params = params;
	}

	@Override
	public void normalize() {}

	@Override
	public String toString() {
		StringJoiner joiner = new StringJoiner(", ");
		for (IExpr p : _params) joiner.add(p.toString());
		return _name + "(" + joiner + ")";
	}
}
