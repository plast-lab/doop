package org.clyze.deepdoop.datalog.element;

import org.clyze.deepdoop.actions.IVisitor;
import org.clyze.deepdoop.datalog.BinOperator;
import org.clyze.deepdoop.datalog.expr.BinaryExpr;
import org.clyze.deepdoop.datalog.expr.IExpr;
import org.clyze.deepdoop.system.SourceLocation;

public class ComparisonElement implements IElement {

	public final BinaryExpr expr;
	SourceLocation          _loc;

	public ComparisonElement(IExpr left, BinOperator op, IExpr right) {
		this(left, op, right, null);
	}
	public ComparisonElement(IExpr left, BinOperator op, IExpr right, SourceLocation loc) {
		this.expr = new BinaryExpr(left, op, right);
		this._loc = loc;
	}
	public ComparisonElement(BinaryExpr expr) {
		this(expr, null);
	}
	public ComparisonElement(BinaryExpr expr, SourceLocation loc) {
		this.expr = expr;
		this._loc = loc;
	}


	@Override
	public <T> T accept(IVisitor<T> v) {
		return v.visit(this);
	}
	@Override
	public SourceLocation location() {
		return _loc;
	}
}
