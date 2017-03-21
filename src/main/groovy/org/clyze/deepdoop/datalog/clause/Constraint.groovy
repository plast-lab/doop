package org.clyze.deepdoop.datalog.clause

import org.clyze.deepdoop.actions.*
import org.clyze.deepdoop.datalog.element.IElement
import org.clyze.deepdoop.system.*

class Constraint implements IVisitable, ISourceItem {

	public final IElement head
	public final IElement body

	Constraint(IElement head, IElement body) {
		this.head = head
		this.body = body
		this._loc = SourceManager.v().getLastLoc()
	}


	@Override
	<T> T accept(IVisitor<T> v) { return v.visit(this) }

	String toString() { return "$head -> $body." }

	SourceLocation _loc
	SourceLocation location() { return _loc }
}
