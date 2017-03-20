package org.clyze.deepdoop.datalog.element

import org.clyze.deepdoop.actions.IVisitor
import org.clyze.deepdoop.datalog.BinOperator
import org.clyze.deepdoop.datalog.expr.*
import org.clyze.deepdoop.system.*

class ComparisonElement implements IElement {

	public final BinaryExpr expr

	ComparisonElement(IExpr left, BinOperator op, IExpr right) {
		this.expr = new BinaryExpr(left, op, right)
		this._loc = SourceManager.v().getLastLoc()
	}
	ComparisonElement(BinaryExpr expr) {
		this.expr = expr
		this._loc = SourceManager.v().getLastLoc()
	}


	@Override
	List<VariableExpr> getVars() { return expr.getVars() }
	@Override
	<T> T accept(IVisitor<T> v) { return v.visit(this) }

	String toString() { return expr.toString() }

	SourceLocation _loc
	SourceLocation location() { return _loc }
}
