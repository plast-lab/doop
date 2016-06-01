package deepdoop.datalog;

import java.util.Map;

public class ComparisonElement implements IElement {

	ComplexExpr _expr;

	ComparisonElement(ComplexExpr expr) {
		_expr = expr;
	}
	public ComparisonElement(IExpr left, BinOperator op, IExpr right) {
		_expr = new ComplexExpr(left, op, right);
	}

	@Override
	public ComparisonElement init(Initializer ini) {
		return new ComparisonElement(_expr.init(ini));
	}

	@Override
	public Map<String, IAtom> getAtoms() {
		return _expr.getAtoms();
	}

	@Override
	public String toString() {
		return _expr.toString();
	}
}
