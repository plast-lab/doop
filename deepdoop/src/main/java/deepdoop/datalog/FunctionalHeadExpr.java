package deepdoop.datalog;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

public class FunctionalHeadExpr implements IExpr {

	String      _name;
	String      _stage;
	List<IExpr> _keyExprs;

	public FunctionalHeadExpr(String name, String stage, List<IExpr> keyExprs) {
		_name     = name;
		_stage    = stage;
		_keyExprs = keyExprs;
	}

	@Override
	public IExpr init(String id) {
		List<IExpr> newKeyExprs = new ArrayList<>();
		for (IExpr e : _keyExprs) newKeyExprs.add(e.init(id));
		return new FunctionalHeadExpr(Names.nameId(_name, id), _stage, newKeyExprs);
	}

	@Override
	public String toString() {
		StringJoiner joiner = new StringJoiner(", ");
		for (IExpr e : _keyExprs) joiner.add(e.toString());
		return _name + "[" + joiner + "]";
	}
}
