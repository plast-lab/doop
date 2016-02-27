package deepdoop.datalog;

public class ComplexExpr implements IExpr {

	public enum Operator {
		PLUS("+"), MINUS("-"), MULT("*"), DIV("/");

		private String _op;

		Operator(String op) {
			_op = op;
		}

		@Override
		public String toString() {
			return _op;
		}
	}

	IExpr _left;
	Operator _op;
	IExpr _right;

	IExpr _expr;

	public ComplexExpr(IExpr left, Operator op, IExpr right) {
		_left = left;
		_op = op;
		_right = right;
	}
	public ComplexExpr(IExpr expr) {
		_expr = expr;
	}

	@Override
	public String toString() {
		if (_op != null) return _left + " " + _op + " " + _right;
		else /*if (_expr != null)*/ return "(" + _expr + ")";
	}
}
