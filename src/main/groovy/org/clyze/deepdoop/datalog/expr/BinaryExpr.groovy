package org.clyze.deepdoop.datalog.expr

import org.clyze.deepdoop.actions.IVisitor
import org.clyze.deepdoop.datalog.BinOperator

class BinaryExpr implements IExpr {

	IExpr       left
	BinOperator op
	IExpr       right

	List<VariableExpr> getVars() { left.getVars() + right.getVars() }

	def <T> T accept(IVisitor<T> v) { v.visit(this) }

	String toString() { "$left $op $right" }
}