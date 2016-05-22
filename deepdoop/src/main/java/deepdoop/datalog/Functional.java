package deepdoop.datalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

public class Functional implements IAtom {

	String      _name;
	int         _arity;
	List<IExpr> _keyExprs;
	IExpr       _valueExpr;
	// Declaration
	List<IAtom> _keyTypes;
	IAtom       _valueType;
	// Instance
	String      _stage;
	String      _backtick;
	boolean     _inDecl;


	public Functional(String name, List<IExpr> keyExprs, IExpr valueExpr, List<IAtom> keyTypes, IAtom valueType) {
		assert (keyTypes == null && valueType == null) || keyExprs.size() == keyTypes.size();
		_name      = name;
		_arity     = keyExprs.size() + 1;
		_keyExprs  = keyExprs;
		_valueExpr = valueExpr;
		_keyTypes  = keyTypes;
		_valueType = valueType;
		_inDecl    = true;
	}
	public Functional(String name, List<IExpr> keyExprs, IExpr valueExpr) {
		this(name, keyExprs, valueExpr, null, null);
	}
	public Functional(String name, String stage, List<IExpr> keyExprs, IExpr valueExpr) {
		_name      = name;
		_arity     = keyExprs.size() + 1;
		_keyExprs  = keyExprs;
		_valueExpr = valueExpr;
		_stage     = stage;
		_inDecl    = false;
	}
	public Functional(String name, String backtick, IExpr valueExpr) {
		_name      = name;
		_arity     = 2;
		_backtick  = backtick;
		_valueExpr = valueExpr;
		_inDecl    = false;
	}


	public void setTypes(List<IAtom> keyTypes, IAtom valueType) {
		assert _keyTypes == null && _valueType == null;
		assert _arity == keyTypes.size() + 1;
		_keyTypes  = keyTypes;
		_valueType = valueType;
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
		return IAtom.Type.FUNCTIONAL;
	}

	@Override
	public int arity() {
		return _arity;
	}

	@Override
	public Functional init(String id) {
		if (_inDecl) {
			List<IAtom> newKeyTypes = new ArrayList<>();
			for (IAtom t : _keyTypes) newKeyTypes.add(t.init(id));
			return new Functional(Names.nameId(_name, id), _keyExprs, _valueExpr, newKeyTypes, _valueType.init(id));
		}
		else if (_backtick == null) {
			List<IExpr> newKeyExprs = new ArrayList<>();
			for (IExpr e : _keyExprs) newKeyExprs.add(e.init(id));
			return new Functional(Names.nameId(_name, id), _stage, newKeyExprs, _valueExpr.init(id));
		}
		// TODO directives need revisiting
		else
			return this;
	}

	@Override
	public String toString() {
		if (_inDecl) {
			StringJoiner joiner1 = new StringJoiner(", ");
			StringJoiner joiner2 = new StringJoiner(", ");
			for (int i = 0 ; i < _arity - 1 ; i++) {
				IExpr v = _keyExprs.get(i);
				IAtom t = _keyTypes.get(i);
				joiner1.add(v.toString());
				joiner2.add(t.name() + "(" + v + ")");
			}
			joiner2.add(_valueType.name() + "(" + _valueExpr + ")");
			return _name + "[" + joiner1 + "] = " + _valueExpr + " -> " + joiner2 + ".";
		}
		else if (_backtick == null) {
			StringJoiner joiner = new StringJoiner(", ");
			for (IExpr e : _keyExprs) joiner.add(e.toString());
			return Names.nameStage(_name, _stage) + "[" + joiner + "] = " + _valueExpr;
		}
		else {
			return _name + "[`" + _backtick + "] = " + _valueExpr;
		}
	}
}
