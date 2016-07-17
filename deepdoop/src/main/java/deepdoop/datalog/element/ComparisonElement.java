package deepdoop.datalog.element;

import deepdoop.actions.IVisitor;
import deepdoop.datalog.BinOperator;
import deepdoop.datalog.expr.BinaryExpr;
import deepdoop.datalog.expr.IExpr;

public class ComparisonElement implements IElement {

	public final BinaryExpr expr;

	public ComparisonElement(IExpr left, BinOperator op, IExpr right) {
		this.expr = new BinaryExpr(left, op, right);
	}
	public ComparisonElement(BinaryExpr expr) {
		this.expr = expr;
	}

	@Override
	public String toString() {
		return expr.toString();
	}


	@Override
	public <T> T accept(IVisitor<T> v) {
		return v.visit(this);
	}
}
