package org.clyze.deepdoop.datalog.element

import org.clyze.deepdoop.actions.IVisitor
import org.clyze.deepdoop.datalog.expr.*
import org.clyze.deepdoop.system.*

class GroupElement implements IElement {

	public final IElement element

	GroupElement(IElement element) {
		this.element = element
		this._loc    = SourceManager.v().getLastLoc()
	}


	@Override
	List<VariableExpr> getVars() { return element.getVars() }
	@Override
	<T> T accept(IVisitor<T> v) { return v.visit(this) }

	String toString() { return "($element)" }

	SourceLocation _loc
	SourceLocation location() { return _loc }
}
