package deepdoop.datalog;

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

	IExpr    _left;
	Operator _op;
	IExpr    _right;

	public ComparisonElement(IExpr left, Operator op, IExpr right) {
		_left  = left;
		_op    = op;
		_right = right;
	}

	@Override
	public ComparisonElement init(Initializer ini) {
		return new ComparisonElement(_left.init(ini), _op, _right.init(ini));
	}

	@Override
	public String toString() {
		return _left + " " + _op + " " + _right;
	}
}
