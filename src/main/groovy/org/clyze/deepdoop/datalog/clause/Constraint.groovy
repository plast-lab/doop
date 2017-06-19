package org.clyze.deepdoop.datalog.clause

import groovy.transform.Canonical
import org.clyze.deepdoop.actions.IVisitable
import org.clyze.deepdoop.actions.IVisitor
import org.clyze.deepdoop.datalog.element.IElement
import org.clyze.deepdoop.system.TSourceItem

@Canonical
class Constraint implements IVisitable, TSourceItem {

	IElement head
	IElement body

	def <T> T accept(IVisitor<T> v) { v.visit(this) }
}
