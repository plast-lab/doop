package org.clyze.deepdoop.datalog.element

import org.clyze.deepdoop.actions.IVisitor
import org.clyze.deepdoop.datalog.expr.*
import org.clyze.deepdoop.system.*

class LogicalElement implements IElement {

	enum LogicType { AND, OR }

	LogicType               type
	Set<? extends IElement> elements

	LogicalElement(LogicType type, List<? extends IElement> elements) {
		this(type, [] + elements as Set)
		this.loc = SourceManager.v().getLastLoc()
	}
	LogicalElement(LogicType type, Set<? extends IElement> elements) {
		this.type     = type
		this.elements = elements
		this.loc      = SourceManager.v().getLastLoc()
	}
	LogicalElement(IElement element) {
		this.type     = LogicType.AND
		this.elements = [element]
		this.loc      = SourceManager.v().getLastLoc()
	}

	List<VariableExpr> getVars() { elements.collect{ it.getVars() }.flatten() }

	def <T> T accept(IVisitor<T> v) { v.visit(this) }

	String toString() {
		elements.collect{ it.toString() }.join(type == LogicType.AND ? ', ' : '; ')
	}

	SourceLocation loc
	SourceLocation location() { loc }
}