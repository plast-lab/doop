package org.clyze.deepdoop.datalog.element.atom

import groovy.transform.Canonical
import org.clyze.deepdoop.actions.IVisitor
import org.clyze.deepdoop.datalog.expr.IExpr
import org.clyze.deepdoop.datalog.expr.VariableExpr

@Canonical
class Functional implements IAtom {

	String name
	String stage
	List<IExpr> keyExprs
	IExpr valueExpr

	int getArity() { keyExprs.size() + 1 }

	IAtom newAtom(String stage, List<VariableExpr> vars) {
		newAlias(name, stage, vars)
	}

	IAtom newAlias(String name, String stage, List<VariableExpr> vars) {
		assert arity == vars.size()
		def varsCopy = [] << vars
		def valueVar = varsCopy.pop() as VariableExpr
		return new Functional(name, stage, varsCopy, valueVar)
	}

	def <T> T accept(IVisitor<T> v) { v.visit(this) }
}
