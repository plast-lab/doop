package org.clyze.deepdoop.datalog.element.atom

import org.clyze.deepdoop.actions.IVisitor
import org.clyze.deepdoop.datalog.expr.*

class Entity extends Predicate {

	Entity(String name, String stage, List<IExpr> exprs) {
		super(name, stage, exprs)
	}
	Entity(String name, List<IExpr> exprs) {
		this(name, null, exprs)
	}

	IAtom newAtom(String stage, List<VariableExpr> vars) {
		assert arity() == vars.size()
		assert arity() == 1
		new Entity(name, stage, [] + vars)
	}
	IAtom newAlias(String name, String stage, List<VariableExpr> vars) {
		throw new UnsupportedOperationException()
	}

	def <T> T accept(IVisitor<T> v) { v.visit(this) }
}