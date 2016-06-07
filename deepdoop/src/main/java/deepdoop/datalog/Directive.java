package deepdoop.datalog;

import java.util.List;

public class Directive implements IAtom {

	public final String       name;
	public final String       backtick;
	public final ConstantExpr constant;
	int                       _arity;
	boolean                   _isPredicate;

	Directive(String name, String backtick, ConstantExpr constant, int arity, boolean isPredicate) {
		this.name     = name;
		this.backtick = backtick;
		this.constant = constant;
		_arity        = arity;
		_isPredicate  = isPredicate;
	}
	public Directive(String name, String backtick) {
		this.name     = name;
		this.backtick = backtick;
		this.constant = null;
		_arity        = 1;
		_isPredicate  = true;
	}
	public Directive(String name, String backtick, ConstantExpr constant) {
		this.name     = name;
		this.backtick = backtick;
		this.constant = constant;
		_arity        = (backtick == null ? 1 : 2);
		_isPredicate  = false;
	}

	@Override
	public String name() { return name; }

	@Override
	public String stage() { return null; }

	@Override
	public int arity() { return _arity; }

	@Override
	public List<IExpr> getExprs() { return null; }

	@Override
	public List<VariableExpr> getExprsAsVars() { return null; }

	@Override
	public Directive init(Initializer ini) {
		return backtick == null ? this : new Directive(name, ini.name(backtick), constant, _arity, _isPredicate);
	}

	@Override
	public String toString() {
		String middle = (backtick != null ? "`" + backtick : "");
		if (_isPredicate)
			return name + "(" + middle + ")";
		else
			return name + "[" + middle + "] = " + constant;
	}
}
