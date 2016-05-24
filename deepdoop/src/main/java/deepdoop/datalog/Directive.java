package deepdoop.datalog;

public class Directive implements IAtom {

	String       _name;
	int          _arity;
	Type         _type;
	String       _backtick;
	ConstantExpr _constant;


	Directive(String name, int arity, IAtom.Type type, String backtick, ConstantExpr constant) {
		_name     = name;
		_arity    = arity;
		_type     = type;
		_backtick = backtick;
		_constant = constant;
	}

	public Directive(String name, String backtick) {
		_name     = name;
		_arity    = 1;
		_type     = Type.PREDICATE;
		_backtick = backtick;
	}
	public Directive(String name, String backtick, ConstantExpr constant) {
		_name     = name;
		_arity    = (backtick == null ? 1 : 2);
		_type     = Type.FUNCTIONAL;
		_backtick = backtick;
		_constant = constant;
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
	public Directive init(Initializer ini) {
		return _backtick == null ? this : new Directive(_name, _arity, _type, ini.name(_backtick), _constant);
	}

	@Override
	public String toString() {
		String middle = (_backtick != null ? "`" + _backtick : "");
		if (_type == Type.PREDICATE)
			return _name + "(" + middle + ")";
		else
			return _name + "[" + middle + "] = " + _constant;
	}
}
