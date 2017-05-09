package org.clyze.deepdoop.datalog.clause

import groovy.transform.Canonical
import org.clyze.deepdoop.actions.*
import org.clyze.deepdoop.datalog.element.atom.*

@Canonical
class RefModeDeclaration extends Declaration {

	RefModeDeclaration(RefMode refmode, Predicate entity, Primitive primitive) {
		super(refmode, [entity, primitive] as Set)
	}

	def <T> T accept(IVisitor<T> v) { v.visit(this) }
}
