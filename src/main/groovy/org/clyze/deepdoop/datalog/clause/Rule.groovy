package org.clyze.deepdoop.datalog.clause

import groovy.transform.Canonical
import org.clyze.deepdoop.actions.IVisitable
import org.clyze.deepdoop.actions.IVisitor
import org.clyze.deepdoop.datalog.element.LogicalElement
import org.clyze.deepdoop.system.TSourceItem

@Canonical
class Rule implements IVisitable, TSourceItem {

	LogicalElement head
	LogicalElement body

	def <T> T accept(IVisitor<T> v) { v.visit(this) }
}
