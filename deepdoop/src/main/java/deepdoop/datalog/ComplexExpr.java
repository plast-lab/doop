package deepdoop.datalog;

import java.util.HashMap;
import java.util.Map;

public class ComplexExpr implements IExpr {

	// Binary operation
	IExpr       _left;
	BinOperator _op;
	IExpr      _right;
	// Grouping (parentheses)
	IExpr      _expr;

	public ComplexExpr(IExpr left, BinOperator op, IExpr right) {
		_left  = left;
		_op    = op;
		_right = right;
	}
	public ComplexExpr(IExpr expr) {
		_expr  = expr;
	}

	@Override
	public ComplexExpr init(Initializer ini) {
		if (_op != null) return new ComplexExpr(_left.init(ini), _op, _right.init(ini));
		else             return new ComplexExpr(_expr.init(ini));
	}

	@Override
	public Map<String, IAtom> getAtoms() {
		Map<String, IAtom> map = new HashMap<>();
		if (_op != null) {
			map.putAll(_left.getAtoms());
			map.putAll(_right.getAtoms());
		}
		else
			map.putAll(_expr.getAtoms());
		return map;
	}

	@Override
	public String toString() {
		if (_op != null) return _left + " " + _op + " " + _right;
		else             return "(" + _expr + ")";
	}
}
