package deepdoop.datalog;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

public class FunctionalElement extends PredicateElement {

	IExpr _valueExpr;

	public FunctionalElement(String name, String stage, List<IExpr> keyExprs, IExpr valueExpr) {
		super(name, stage, keyExprs);
		_valueExpr = valueExpr;
	}

	@Override
	public IElement init(String id) {
		List<IExpr> newKeyExprs = new ArrayList<>();
		for (IExpr e : _exprs) newKeyExprs.add(e.init(id));
		return new FunctionalElement(id + ":" + _name, _stage, newKeyExprs, _valueExpr.init(id));
	}

	@Override
	public String toString() {
		StringJoiner joiner = new StringJoiner(", ");
		for (IExpr e : _exprs) joiner.add(e.toString());
		String stageSuffix = "";
		if (_stage != null && _stage.equals("@past")) stageSuffix = ":past";
		else if (_stage != null) stageSuffix = _stage;
		return _name + stageSuffix + "[" + joiner + "] = " + _valueExpr;
	}
}
