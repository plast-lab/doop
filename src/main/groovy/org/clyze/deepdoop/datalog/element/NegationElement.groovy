package org.clyze.deepdoop.datalog.element

import org.clyze.deepdoop.actions.IVisitor
import org.clyze.deepdoop.datalog.expr.*
import org.clyze.deepdoop.system.*

class NegationElement implements IElement {

	IElement element

	NegationElement(IElement element) {
		this.element = element
		this.loc     = SourceManager.v().getLastLoc()
	}

	List<VariableExpr> getVars() { element.getVars() }

	def <T> T accept(IVisitor<T> v) { v.visit(this) }

	String toString() { "!$element" }

	SourceLocation loc
	SourceLocation location() { loc }
}