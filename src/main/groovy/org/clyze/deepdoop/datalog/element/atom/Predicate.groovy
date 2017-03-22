package org.clyze.deepdoop.datalog.element.atom

import org.clyze.deepdoop.actions.IVisitor
import org.clyze.deepdoop.datalog.expr.*
import org.clyze.deepdoop.system.*

class Predicate implements IAtom {

	String      name
	String      stage
	List<IExpr> exprs

	Predicate(String name, String stage, List<IExpr> exprs) {
		this.name  = name
		this.stage = stage
		this.exprs = exprs
	}
	Predicate(String name, List<IExpr> exprs) {
		this(name, null, exprs)
	}

	String name() { name }
	String stage() { stage }
	int arity() { exprs.size() }
	IAtom newAtom(String stage, List<VariableExpr> vars) {
		newAlias(name, stage, vars)
	}
	IAtom newAlias(String name, String stage, List<VariableExpr> vars) {
		assert arity() == vars.size()
		return new Predicate(name, stage, [] + vars)
	}
	List<VariableExpr> getVars() { exprs.collect{ it.getVars() }.flatten() }

	def <T> T accept(IVisitor<T> v) { v.visit(this) }

	String toString() { "$name(${exprs.collect{ it.toString() }.join(', ')})" }

	SourceLocation location() { null }
}