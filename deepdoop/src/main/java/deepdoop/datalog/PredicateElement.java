package deepdoop.datalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

public class PredicateElement implements IElement {

	String      _name;
	String      _stage;
	List<IExpr> _exprs;
	String      _backtick;

	public PredicateElement(String name, String stage, List<IExpr> exprs) {
		_name     = name;
		_stage    = stage;
		_exprs    = exprs;
	}
	public PredicateElement(String name, String backtick) {
		_name     = name;
		_backtick = backtick;
	}

	@Override
	public PredicateElement init(String id) {
		List<IExpr> newExprs = new ArrayList<>();
		for (IExpr e : _exprs) newExprs.add(e.init(id));
		return new PredicateElement(Names.nameId(_name, id), _stage, newExprs);
	}

	@Override
	public Map<String, IAtom> getAtoms() {
		return Collections.singletonMap(_name, new BareAtom(_name, IAtom.Type.PREDICATE, arity()));
	}

	public String name() {
		return _name;
	}

	public int arity() {
		return _exprs.size();
	}

	@Override
	public String toString() {
		if (_backtick == null) {
			StringJoiner joiner = new StringJoiner(", ");
			for (IExpr e : _exprs) joiner.add(e.toString());
			return Names.nameStage(_name, _stage) + "(" + joiner + ")";
		}
		else
			return _name + "(`" + _backtick + ")";
	}
}
