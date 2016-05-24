package deepdoop.datalog;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

public class Predicate implements IAtom {

	String      _name;
	int         _arity;
	List<IExpr> _exprs;
	// Declaration
	List<IAtom> _types;
	// Instance
	String      _stage;
	boolean     _inDecl;


	public Predicate(String name, List<IExpr> exprs, List<IAtom> types) {
		assert types == null || exprs.size() == types.size();
		// Initially, treat every predicate as an Entity
		if (types == null) types = new ArrayList<>();
		_name     = name;
		_arity    = exprs.size();
		_exprs    = exprs;
		_types    = types;
		_inDecl   = true;
	}
	public Predicate(String name, List<IExpr> exprs) {
		this(name, exprs, null);
	}
	public Predicate(String name, String stage, List<IExpr> exprs) {
		_name     = name;
		_arity    = exprs.size();
		_exprs    = exprs;
		_stage    = stage;
		_inDecl   = false;
	}


	public void setTypes(List<IAtom> types) {
		assert _arity == types.size();
		_types = types;
	}

	@Override
	public String name() {
		return _name;
	}

	@Override
	public IAtom.Type type() {
		return IAtom.Type.PREDICATE;
	}

	@Override
	public int arity() {
		return _arity;
	}

	@Override
	public List<VariableExpr> getVars() {
		List<VariableExpr> list = new ArrayList<>();
		for (IExpr e : _exprs)
			list.add((e instanceof VariableExpr ? (VariableExpr) e : null));
		return list;
	}

	@Override
	public Predicate init(Initializer ini) {
		List<IExpr> newExprs = new ArrayList<>();
		for (IExpr e : _exprs) newExprs.add(e.init(ini));
		if (_inDecl) {
			List<IAtom> newTypes = new ArrayList<>();
			for (IAtom t : _types) newTypes.add(t.init(ini));
			return new Predicate(ini.name(_name), newExprs, newTypes);
		}
		else
			return new Predicate(ini.name(_name, _stage), ini.stage(_stage), newExprs);
	}

	@Override
	public String toString() {
		if (_inDecl) {
			StringJoiner joiner1 = new StringJoiner(", ");
			StringJoiner joiner2 = new StringJoiner(", ");
			for (int i = 0 ; i < _arity ; i++) {
				IExpr v = _exprs.get(i);
				joiner1.add(v.toString());
				if (i < _types.size()) {
					IAtom t = _types.get(i);
					joiner2.add(t.name() + "(" + v + ")");
				}
			}
			return _name + "(" + joiner1 + ") -> " + joiner2 + ".";
		}
		else {
			StringJoiner joiner = new StringJoiner(", ");
			for (IExpr e : _exprs) joiner.add(e.toString());
			return _name + "(" + joiner + ")";
		}
	}
}
