package org.clyze.deepdoop.datalog.expr

import org.clyze.deepdoop.actions.IVisitor
import org.clyze.deepdoop.datalog.element.atom.Functional

class FunctionalHeadExpr implements IExpr {

	public final Functional functional

	FunctionalHeadExpr(String name, String stage, List<IExpr> keyExprs) {
		this.functional = new Functional(name, stage, keyExprs, null)
	}
	FunctionalHeadExpr(Functional functional) {
		assert functional.valueExpr == null
		this.functional = functional
	}


	@Override
	List<VariableExpr> getVars() { return functional.getVars() }
	@Override
	<T> T accept(IVisitor<T> v) { return v.visit(this) }

	String toString() { return functional.toString() }
}
