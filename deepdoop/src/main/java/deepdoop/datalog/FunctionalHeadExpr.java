package deepdoop.datalog;

import java.util.List;

public class FunctionalHeadExpr implements IExpr {

	String _name;
	List<IExpr> _keyExprs;

	public FunctionalHeadExpr(String name, List<IExpr> keyExprs) {
		_name = name;
		_keyExprs = keyExprs;
	}

	@Override
	public String toString() {
		StringJoiner joiner = new StringJoiner(", ");
		for (IExpr e : _keyExprs) joiner.add(e.toString());
		return _name + "[" + joiner + "]";
	}
}
