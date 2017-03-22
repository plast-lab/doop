package org.clyze.deepdoop.datalog.clause

import org.clyze.deepdoop.actions.*
import org.clyze.deepdoop.datalog.element.IElement
import org.clyze.deepdoop.system.*

class Constraint implements IVisitable, ISourceItem {

	IElement head
	IElement body

	Constraint(IElement head, IElement body) {
		this.head = head
		this.body = body
		this.loc  = SourceManager.v().getLastLoc()
	}

	def <T> T accept(IVisitor<T> v) { v.visit(this) }

	String toString() { "$head -> $body." }

	SourceLocation loc
	SourceLocation location() { loc }
}