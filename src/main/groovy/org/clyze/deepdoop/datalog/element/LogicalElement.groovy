package org.clyze.deepdoop.datalog.element

import groovy.transform.Canonical
import org.clyze.deepdoop.actions.IVisitor
import org.clyze.deepdoop.datalog.expr.VariableExpr

@Canonical
class LogicalElement implements IElement {

	enum LogicType {
		AND, OR
	}

	LogicType type
	List<IElement> elements

	LogicalElement(LogicType type, List<IElement> elements) {
		this.type = type
		this.elements = elements
	}

	LogicalElement(IElement element) {
		this.type = LogicType.AND
		this.elements = [element]
	}

	List<VariableExpr> getVars() { elements.collect { it.vars }.flatten() }

	def <T> T accept(IVisitor<T> v) { v.visit(this) }
}
