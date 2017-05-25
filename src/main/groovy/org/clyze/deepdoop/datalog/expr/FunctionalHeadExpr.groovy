package org.clyze.deepdoop.datalog.expr

import groovy.transform.Canonical
import org.clyze.deepdoop.actions.IVisitor
import org.clyze.deepdoop.datalog.element.atom.Functional

@Canonical
class FunctionalHeadExpr implements IExpr {

	Functional functional

	FunctionalHeadExpr(String name, String stage, List<IExpr> keyExprs) {
		this.functional = new Functional(name, stage, keyExprs, null)
	}

	FunctionalHeadExpr(Functional functional) {
		assert functional.valueExpr == null
		this.functional = functional
	}

	List<VariableExpr> getVars() { functional.vars }

	def <T> T accept(IVisitor<T> v) { v.visit(this) }
}
