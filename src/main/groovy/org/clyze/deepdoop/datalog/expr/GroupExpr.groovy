package org.clyze.deepdoop.datalog.expr

import org.clyze.deepdoop.actions.IVisitor

class GroupExpr implements IExpr {

	IExpr expr

	List<VariableExpr> getVars() { expr.getVars() }

	def <T> T accept(IVisitor<T> v) { v.visit(this) }

	String toString() { expr.toString() }
}