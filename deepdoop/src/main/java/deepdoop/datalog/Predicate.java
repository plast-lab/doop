package deepdoop.datalog;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

public class Predicate implements IAtom {

	String      _name;
	String      _stage;
	List<IExpr> _exprs;

	public Predicate(String name, String stage, List<IExpr> exprs) {
		_name     = name;
		_stage    = stage;
		_exprs    = exprs;
	}
	public Predicate(String name, List<IExpr> exprs) {
		this(name, null, exprs);
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
		return _exprs.size();
	}

	@Override
	public List<IExpr> getExprs() {
		return _exprs;
	}

	@Override
	public List<VariableExpr> getExprsAsVars() {
		List<VariableExpr> list = new ArrayList<>();
		for (IExpr e : _exprs) list.add((e instanceof VariableExpr ? (VariableExpr) e : null));
		return list;
	}

	@Override
	public Predicate init(Initializer ini) {
		List<IExpr> newExprs = new ArrayList<>();
		for (IExpr e : _exprs) newExprs.add(e.init(ini));
		return new Predicate(ini.name(_name, _stage), ini.stage(_stage), newExprs);
	}

	@Override
	public String toString() {
		StringJoiner joiner = new StringJoiner(", ");
		for (IExpr e : _exprs) joiner.add(e.toString());
		return _name + (_stage == null ? "" : "@"+_stage) + "(" + joiner + ")";
	}
}
