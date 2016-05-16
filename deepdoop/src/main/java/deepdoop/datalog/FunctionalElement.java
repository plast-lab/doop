package deepdoop.datalog;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

public class FunctionalElement implements IElement {

	String      _name;
	String      _stage;
	List<IExpr> _keyExprs;
	IExpr       _valueExpr;

	public FunctionalElement(String name, String stage, List<IExpr> keyExprs, IExpr valueExpr) {
		_name      = name;
		_stage     = stage;
		_keyExprs  = keyExprs;
		_valueExpr = valueExpr;
	}

	@Override
	public FunctionalElement init(String id) {
		List<IExpr> newKeyExprs = new ArrayList<>();
		for (IExpr e : _keyExprs) newKeyExprs.add(e.init(id));
		return new FunctionalElement(id + ":" + _name, _stage, newKeyExprs, _valueExpr.init(id));
	}

	public String name() {
		return _name;
	}

	public int arity() {
		return _keyExprs.size() + 1;
	}

	@Override
	public String toString() {
		StringJoiner joiner = new StringJoiner(", ");
		for (IExpr e : _keyExprs) joiner.add(e.toString());
		String stageSuffix = "";
		if (_stage != null && _stage.equals("@past")) stageSuffix = ":past";
		else if (_stage != null) stageSuffix = _stage;
		return _name + stageSuffix + "[" + joiner + "] = " + _valueExpr;
	}
}
