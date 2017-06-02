package org.clyze.deepdoop.datalog.element

import groovy.transform.Canonical
import org.clyze.deepdoop.actions.IVisitor
import org.clyze.deepdoop.datalog.element.atom.Predicate
import org.clyze.deepdoop.datalog.expr.VariableExpr

@Canonical
class AggregationElement implements IElement {

	VariableExpr var
	Predicate predicate
	IElement body

	def <T> T accept(IVisitor<T> v) { v.visit(this) }
}
