package deepdoop.datalog;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

public class Predicate implements IAtom {

	String      _name;
	int         _arity;
	List<IExpr> _exprs;
	// Declaration
	List<IAtom> _types;
	// Instance
	String      _stage;
	String      _backtick;
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
	public Predicate(String name, String backtick) {
		_name     = name;
		_arity    = 1;
		_backtick = backtick;
		_inDecl   = false;
	}


	public void setTypes(List<IAtom> types) {
		assert _arity == types.size();
		_types = types;
	}

	@Override
	public Map<String, IAtom> getAtoms() {
		return Collections.singletonMap(_name, new Atom(name(), type(), arity()));
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
	public Predicate init(String id) {
		if (_inDecl) {
			// expressions in a declaration are only variables; no need for init
			List<IAtom> newTypes = new ArrayList<>();
			for (IAtom t : _types) newTypes.add(t.init(id));
			return new Predicate(Names.nameId(_name, id), _exprs, newTypes);
		}
		else if (_backtick == null) {
			List<IExpr> newExprs = new ArrayList<>();
			for (IExpr e : _exprs) newExprs.add(e.init(id));
			return new Predicate(Names.nameId(_name, id), _stage, newExprs);
		}
		// TODO directives need revisiting
		else {
			return this;
		}
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
		else if (_backtick == null) {
			StringJoiner joiner = new StringJoiner(", ");
			for (IExpr e : _exprs) joiner.add(e.toString());
			return Names.nameStage(_name, _stage) + "(" + joiner + ")";
		}
		else {
			return _name + "(`" + _backtick + ")";
		}
	}
}
