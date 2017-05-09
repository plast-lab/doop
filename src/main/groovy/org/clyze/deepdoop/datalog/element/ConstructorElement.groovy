package org.clyze.deepdoop.datalog.element

import groovy.transform.Canonical
import org.clyze.deepdoop.actions.IVisitor
import org.clyze.deepdoop.datalog.element.atom.*

@Canonical
class ConstructorElement implements IElement {

	@Delegate Functional constructor
	IAtom type

	def <T> T accept(IVisitor<T> v) { v.visit(this) }
}
