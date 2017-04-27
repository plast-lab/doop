package org.clyze.deepdoop.datalog.element.atom

import org.clyze.deepdoop.actions.IVisitor
import org.clyze.deepdoop.datalog.expr.*

class Entity extends Predicate {

	Entity(String name, String stage, IExpr expr) {
		super(name, stage, [expr])
	}
	Entity(String name, IExpr expr) {
		this(name, null, [expr])
	}

	IAtom newAtom(String stage, List<VariableExpr> vars) {
		assert arity() == vars.size()
		assert arity() == 1
		new Entity(name, stage, vars.first())
	}
	IAtom newAlias(String name, String stage, List<VariableExpr> vars) {
		throw new UnsupportedOperationException()
	}

	def <T> T accept(IVisitor<T> v) { v.visit(this) }
}
