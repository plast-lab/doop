package deepdoop.datalog;

import java.util.List;

public class ComparisonElement implements IElement {

	public enum Operator {
		EQ("="), LT("<"), LEQ("<="),
		GT(">"), GEQ(">="), NEQ("!=");

		private String _op;

		Operator(String op) {
			_op = op;
		}

		@Override
		public String toString() {
			return _op;
		}
	}

	ExprElement _left;
	Operator _op;
	ExprElement _right;

	public ComparisonElement(ExprElement left, Operator op, ExprElement right) {
		_left = left;
		_op = op;
		_right = right;
	}

	@Override
	public void normalize() {}

	@Override
	public String toString() {
		return _left + " " + _op + " " + _right;
	}
}
