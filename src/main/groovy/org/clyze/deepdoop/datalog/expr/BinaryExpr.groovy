package org.clyze.deepdoop.datalog.expr

import org.clyze.deepdoop.actions.*
import org.clyze.deepdoop.datalog.*

class BinaryExpr implements IExpr {

	public final IExpr       left
	public final BinOperator op
	public final IExpr       right

	BinaryExpr(IExpr left, BinOperator op, IExpr right) {
		this.left  = left
		this.op    = op
		this.right = right
	}


	@Override
	List<VariableExpr> getVars() {
		return left.getVars() + right.getVars()
		//def list = []
		//list.addAll(left.getVars())
		//list.addAll(right.getVars())
		//return list
	}
	@Override
	<T> T accept(IVisitor<T> v) { return v.visit(this) }

	String toString() { return "$left $op $right" }
}
