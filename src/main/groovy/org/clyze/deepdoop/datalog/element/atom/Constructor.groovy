package org.clyze.deepdoop.datalog.element.atom

import groovy.transform.Canonical
import groovy.transform.EqualsAndHashCode
import org.clyze.deepdoop.actions.IVisitor
import org.clyze.deepdoop.datalog.expr.VariableExpr

@Canonical
@EqualsAndHashCode(callSuper = true)
class Constructor extends Functional {

	// The constructed entity
	IAtom entity

	Constructor(Functional f, IAtom entity) {
		super(f.name, f.stage, f.keyExprs, f.valueExpr)
		this.entity = entity
	}

	def getEntity() {
		if (entity instanceof Stub)
			entity = new Entity(entity.name, valueExpr)
		return entity
	}

	IAtom newAlias(String name, String stage, List<VariableExpr> vars) {
		new Constructor(super.newAlias(name, stage, vars))
	}

	def <T> T accept(IVisitor<T> v) { v.visit(this) }
}
