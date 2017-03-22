package org.clyze.deepdoop.datalog.element.atom

import org.clyze.deepdoop.actions.IVisitor
import org.clyze.deepdoop.datalog.expr.*
import org.clyze.deepdoop.system.*

class Functional implements IAtom {

	String      name
	String      stage
	List<IExpr> keyExprs
	IExpr       valueExpr

	Functional(String name, String stage, List<IExpr> keyExprs, IExpr valueExpr) {
		this.name      = name
		this.stage     = stage
		this.keyExprs  = keyExprs
		this.valueExpr = valueExpr
	}

	String name() { name }
	String stage() { stage }
	int arity() { keyExprs.size() + 1 }
	IAtom newAtom(String stage, List<VariableExpr> vars) {
		newAlias(name, stage, vars)
	}
	IAtom newAlias(String name, String stage, List<VariableExpr> vars) {
		assert arity() == vars.size()
		def varsCopy = [] << vars
		def valueVar = varsCopy.pop()
		return new Functional(name, stage, varsCopy, valueVar)
	}
	List<VariableExpr> getVars() {
		def list = keyExprs.collect{ it.getVars() }.flatten()
		if (valueExpr != null) list += valueExpr.getVars()
		return list
	}

	def <T> T accept(IVisitor<T> v) { v.visit(this) }

	String toString() {
		def exprStr = keyExprs.collect{ it.toString() }.join(', ')
		return "$name[$exprStr]" + (valueExpr == null ? '' : " = $valueExpr")
	}

	SourceLocation location() { null }
}