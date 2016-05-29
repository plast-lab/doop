package deepdoop.datalog;

import java.util.List;

public class Directive implements IAtom {

	String       _name;
	String       _backtick;
	ConstantExpr _constant;
	int          _arity;
	boolean      _isPredicate;

	Directive(String name, String backtick, ConstantExpr constant, int arity, boolean isPredicate) {
		_name        = name;
		_backtick    = backtick;
		_constant    = constant;
		_arity       = arity;
		_isPredicate = isPredicate;
	}
	public Directive(String name, String backtick) {
		_name        = name;
		_backtick    = backtick;
		_arity       = 1;
		_isPredicate = true;
	}
	public Directive(String name, String backtick, ConstantExpr constant) {
		_name        = name;
		_backtick    = backtick;
		_constant    = constant;
		_arity       = (backtick == null ? 1 : 2);
		_isPredicate = false;
	}

	@Override
	public String name() {
		return _name;
	}

	@Override
	public String stage() { return null; }

	@Override
	public int arity() {
		return _arity;
	}

	@Override
	public List<IExpr> getExprs() { return null; }

	@Override
	public List<VariableExpr> getExprsAsVars() { return null; }

	@Override
	public Directive init(Initializer ini) {
		return _backtick == null ? this : new Directive(_name, ini.name(_backtick), _constant, _arity, _isPredicate);
	}

	@Override
	public String toString() {
		String middle = (_backtick != null ? "`" + _backtick : "");
		if (_isPredicate)
			return _name + "(" + middle + ")";
		else
			return _name + "[" + middle + "] = " + _constant;
	}
}
