package org.clyze.deepdoop.datalog.element;

import org.clyze.deepdoop.actions.IVisitor;
import org.clyze.deepdoop.datalog.BinOperator;
import org.clyze.deepdoop.datalog.expr.BinaryExpr;
import org.clyze.deepdoop.datalog.expr.IExpr;

public class ComparisonElement implements IElement {

	public final BinaryExpr expr;

	public ComparisonElement(IExpr left, BinOperator op, IExpr right) {
		this.expr = new BinaryExpr(left, op, right);
	}
	public ComparisonElement(BinaryExpr expr) {
		this.expr = expr;
	}


	@Override
	public <T> T accept(IVisitor<T> v) {
		return v.visit(this);
	}
}
