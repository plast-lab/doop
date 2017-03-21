package org.clyze.deepdoop.datalog.clause

import org.clyze.deepdoop.actions.*
import org.clyze.deepdoop.datalog.element.atom.*

class RefModeDeclaration extends Declaration {

	RefModeDeclaration(RefMode refmode, Predicate entity, Primitive primitive) {
		super(refmode, new HashSet<>(Arrays.asList(entity, primitive)))
	}


	@Override
	<T> T accept(IVisitor<T> v) { return v.visit(this) }

	String toString() { return "${types.get(0)}, $atom -> ${types.get(1)}" }
}
