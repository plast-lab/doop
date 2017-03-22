package org.clyze.deepdoop.datalog.clause

import org.clyze.deepdoop.actions.*
import org.clyze.deepdoop.datalog.element.atom.*

class RefModeDeclaration extends Declaration {

	RefModeDeclaration(RefMode refmode, Predicate entity, Primitive primitive) {
		super(refmode, [entity, primitive] as Set)
	}

	def <T> T accept(IVisitor<T> v) { v.visit(this) }

	String toString() { "${types[0]}, $atom -> ${types[1]}" }
}