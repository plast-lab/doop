package org.clyze.deepdoop.datalog.expr

import org.clyze.deepdoop.actions.IVisitor
import org.clyze.deepdoop.datalog.BinOperator

class BinaryExpr implements IExpr {

	IExpr       left
	BinOperator op
	IExpr       right

	BinaryExpr(IExpr left, BinOperator op, IExpr right) {
		this.left  = left
		this.op    = op
		this.right = right
	}

	List<VariableExpr> getVars() { left.getVars() + right.getVars() }

	def <T> T accept(IVisitor<T> v) { v.visit(this) }

	String toString() { "$left $op $right" }
}