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

	@Override
	IAtom instantiate(String stage, List<VariableExpr> vars) {
		assert arity() == vars.size()
		assert arity() == 1
		return new Entity(name, stage, vars.collect())
	}
	@Override
	<T> T accept(IVisitor<T> v) { return v.visit(this) }
}

