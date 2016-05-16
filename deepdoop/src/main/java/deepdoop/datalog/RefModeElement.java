package deepdoop.datalog;

import java.util.StringJoiner;

public class RefModeElement implements IElement {

	String       _name;
	String       _stage;
	VariableExpr _var;
	IExpr        _primitiveExpr;

	public RefModeElement(String name, String stage, VariableExpr var, IExpr primitiveExpr) {
		_name          = name;
		_stage         = stage;
		_var           = var;
		_primitiveExpr = primitiveExpr;
	}

	@Override
	public RefModeElement init(String id) {
		return new RefModeElement(id + ":" + _name, _stage, _var, _primitiveExpr);
	}

	public String name() {
		return _name;
	}

	public int arity() {
		return 2;
	}

	@Override
	public String toString() {
		return _name + "(" + _var + ":" + _primitiveExpr + ")";
	}
}
