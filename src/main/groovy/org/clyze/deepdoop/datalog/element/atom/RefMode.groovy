package org.clyze.deepdoop.datalog.element.atom

import org.clyze.deepdoop.actions.IVisitor
import org.clyze.deepdoop.datalog.expr.*
import org.clyze.deepdoop.system.*

class RefMode implements IAtom {

	String       name
	String       stage
	VariableExpr entityVar
	IExpr        valueExpr

	RefMode(String name, String stage, VariableExpr entityVar, IExpr valueExpr) {
		assert stage != "@past"
		this.name      = name
		this.stage     = stage
		this.entityVar = entityVar
		this.valueExpr = valueExpr
	}

	String name() { name }
	String stage() { stage }
	int arity() { 2 }
	IAtom newAtom(String stage, List<VariableExpr> vars) {
		assert arity() == vars.size()
		return new RefMode(name, stage, vars[0], vars[1])
	}
	IAtom newAlias(String name, String stage, List<VariableExpr> vars) {
		throw new UnsupportedOperationException()
	}
	List<VariableExpr> getVars() { [entityVar] + valueExpr.getVars() }

	def <T> T accept(IVisitor<T> v) { v.visit(this) }

	String toString() { "$name($entityVar:$valueExpr)" }

	SourceLocation location() { null }
}