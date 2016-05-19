package deepdoop.datalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

public class FunctionalElement implements IElement {

	String      _name;
	String      _stage;
	List<IExpr> _keyExprs;
	IExpr       _valueExpr;
	String      _backtick;

	public FunctionalElement(String name, String stage, List<IExpr> keyExprs, IExpr valueExpr) {
		_name      = name;
		_stage     = stage;
		_keyExprs  = keyExprs;
		_valueExpr = valueExpr;
	}
	public FunctionalElement(String name, String backtick, IExpr valueExpr) {
		_name      = name;
		_backtick  = backtick;
		_valueExpr = valueExpr;
	}

	@Override
	public FunctionalElement init(String id) {
		List<IExpr> newKeyExprs = new ArrayList<>();
		for (IExpr e : _keyExprs) newKeyExprs.add(e.init(id));
		return new FunctionalElement(Names.nameId(_name, id), _stage, newKeyExprs, _valueExpr.init(id));
	}

	@Override
	public Map<String, IAtom> getAtoms() {
		return Collections.singletonMap(_name, new BareAtom(_name, IAtom.Type.FUNCTIONAL, arity()));
	}

	public String name() {
		return _name;
	}

	public int arity() {
		return _keyExprs.size() + 1;
	}

	@Override
	public String toString() {
		if (_backtick == null) {
			StringJoiner joiner = new StringJoiner(", ");
			for (IExpr e : _keyExprs) joiner.add(e.toString());
			return Names.nameStage(_name, _stage) + "[" + joiner + "] = " + _valueExpr;
		}
		else
			return _name + "[`" + _backtick + "] = " + _valueExpr;
	}
}
