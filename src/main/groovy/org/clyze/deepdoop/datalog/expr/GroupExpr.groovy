package org.clyze.deepdoop.datalog.expr

import groovy.transform.Canonical
import org.clyze.deepdoop.actions.IVisitor

@Canonical
class GroupExpr implements IExpr {

	@Delegate
	IExpr expr

	def <T> T accept(IVisitor<T> v) { v.visit(this) }
}
