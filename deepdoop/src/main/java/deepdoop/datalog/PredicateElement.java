package deepdoop.datalog;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

public class PredicateElement implements IElement {

	protected String      _name;
	protected String      _stage;
	protected List<IExpr> _exprs;

	public PredicateElement(String name, String stage, List<IExpr> exprs) {
		_name  = name;
		_stage = stage;
		_exprs = exprs;
	}

	@Override
	public IElement init(String id) {
		List<IExpr> newExprs = new ArrayList<>();
		for (IExpr e : _exprs) newExprs.add(e.init(id));
		return new PredicateElement(id + ":" + _name, _stage, newExprs);
	}

	@Override
	public String toString() {
		StringJoiner joiner = new StringJoiner(", ");
		for (IExpr e : _exprs) joiner.add(e.toString());
		String stageSuffix = "";
		if (_stage != null && _stage.equals("@past")) stageSuffix = ":past";
		else if (_stage != null) stageSuffix = _stage;
		return _name + stageSuffix + "(" + joiner + ")";
	}
}
