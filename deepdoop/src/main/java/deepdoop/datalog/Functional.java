package deepdoop.datalog;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

public class Functional implements IAtom {

	String      _name;
	String      _stage;
	List<IExpr> _keyExprs;
	IExpr       _valueExpr;

	public Functional(String name, String stage, List<IExpr> keyExprs, IExpr valueExpr) {
		_name      = name;
		_stage     = stage;
		_keyExprs  = keyExprs;
		_valueExpr = valueExpr;
	}

	@Override
	public String name() {
		return _name;
	}

	@Override
	public String stage() {
		return _stage;
	}

	@Override
	public int arity() {
		return _keyExprs.size() + 1;
	}

	@Override
	public List<IExpr> getExprs() {
		List<IExpr> list = new ArrayList<>(_keyExprs);
		list.add(_valueExpr);
		return list;
	}

	@Override
	public List<VariableExpr> getExprsAsVars() {
		List<VariableExpr> list = new ArrayList<>();
		for (IExpr e : _keyExprs) list.add((e instanceof VariableExpr ? (VariableExpr) e : null));
		list.add((_valueExpr instanceof VariableExpr ? (VariableExpr) _valueExpr : null));
		return list;
	}

	@Override
	public Functional init(Initializer ini) {
		List<IExpr> newKeyExprs = new ArrayList<>();
		for (IExpr e : _keyExprs) newKeyExprs.add(e.init(ini));
		return new Functional(ini.name(_name, _stage), ini.stage(_stage), newKeyExprs, _valueExpr.init(ini));
	}

	@Override
	public String toString() {
		StringJoiner joiner = new StringJoiner(", ");
		for (IExpr e : _keyExprs) joiner.add(e.toString());
		return _name + (_stage == null ? "" : "@"+_stage) + "[" + joiner + "] = " + _valueExpr;
	}
}
