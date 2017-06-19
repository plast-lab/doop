package org.clyze.deepdoop.datalog.element.atom

import groovy.transform.Canonical
import org.clyze.deepdoop.actions.IVisitor
import org.clyze.deepdoop.datalog.expr.IExpr
import org.clyze.deepdoop.datalog.expr.VariableExpr

@Canonical
class RefMode implements IAtom {

	String name
	String stage
	VariableExpr entityVar
	IExpr valueExpr

	int getArity() { 2 }

	IAtom newAtom(String stage, List<VariableExpr> vars) {
		assert arity == vars.size()
		return new RefMode(name, stage, vars[0], vars[1])
	}

	IAtom newAlias(String name, String stage, List<VariableExpr> vars) {
		throw new UnsupportedOperationException()
	}

	def <T> T accept(IVisitor<T> v) { v.visit(this) }
}
