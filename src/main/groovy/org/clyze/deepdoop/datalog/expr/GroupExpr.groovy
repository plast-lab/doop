package org.clyze.deepdoop.datalog.expr

import org.clyze.deepdoop.actions.IVisitor

class GroupExpr implements IExpr {

	public final IExpr expr

	GroupExpr(IExpr expr) {
		this.expr = expr
	}


	@Override
	List<VariableExpr> getVars() { return expr.getVars() }
	@Override
	<T> T accept(IVisitor<T> v) { return v.visit(this) }

	String toString() { return expr.toString() }
}
