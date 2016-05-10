package deepdoop.datalog;

import java.util.List;

public class FunctionalHeadExpr implements IExpr {

	String _name;
	String _stage;
	List<IExpr> _keyExprs;

	public FunctionalHeadExpr(String name, String stage, List<IExpr> keyExprs) {
		_name = name;
		_stage = stage;
		_keyExprs = keyExprs;
	}

	@Override
	public String toString() {
		StringJoiner joiner = new StringJoiner(", ");
		for (IExpr e : _keyExprs) joiner.add(e.toString());
		return _name + "[" + joiner + "]";
	}
}
