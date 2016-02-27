package deepdoop.datalog;

import java.util.List;

public class FunctionalHeadExpr implements IExpr {
	String _name;
	List<IExpr> _keyParams;

	public FunctionalHeadExpr(String name, List<IExpr> keyParams) {
		_name = name;
		_keyParams = keyParams;
	}

	@Override
	public String toString() {
		StringJoiner joiner = new StringJoiner(", ");
		for (IExpr p : _keyParams) joiner.add(p.toString());
		return _name + "[" + joiner + "]";
	}
}
