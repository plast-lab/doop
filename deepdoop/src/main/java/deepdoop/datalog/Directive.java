package deepdoop.datalog;

import deepdoop.datalog.IAtom.Type;

public class Directive implements IAtom {

	String _name;
	int    _arity;
	Type   _type;
	String _backtick;
	IExpr  _valueExpr;

	Directive(String name, int arity, Type type, String backtick, IExpr valueExpr) {
		_name      = name;
		_arity     = arity;
		_type      = type;
		_backtick  = backtick;
		_valueExpr = valueExpr;
	}
	public Directive(String name, String backtick) {
		_name      = name;
		_arity     = 1;
		_type      = Type.PREDICATE;
		_backtick  = backtick;
	}
	public Directive(String name, String backtick, IExpr valueExpr) {
		_name      = name;
		_arity     = (backtick == null ? 1 : 2);
		_type      = Type.FUNCTIONAL;
		_backtick  = backtick;
		_valueExpr = valueExpr;
	}

	@Override
	public String name() {
		return _name;
	}

	@Override
	public IAtom.Type type() {
		return _type;
	}

	@Override
	public int arity() {
		return _arity;
	}

	@Override
	public Directive init(String id) {
		return _backtick == null ? this : new Directive(_name, _arity, _type, Names.nameId(_backtick, id), _valueExpr);
	}

	@Override
	public String toString() {
		String middle = (_backtick != null ? "`" + _backtick : "");
		if (_type == Type.PREDICATE)
			return _name + "(" + middle + ")";
		else
			return _name + "[" + middle + "] = " + _valueExpr;
	}
}
