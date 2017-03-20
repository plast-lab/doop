package org.clyze.deepdoop.datalog.clause

import org.clyze.deepdoop.actions.*
import org.clyze.deepdoop.datalog.element.*
import org.clyze.deepdoop.datalog.element.atom.*
import org.clyze.deepdoop.system.*

class Rule implements IVisitable, ISourceItem {

	public final LogicalElement head
	public final IElement       body
	public final boolean        isDirective

	Rule(LogicalElement head, IElement body) {
		this(head, body, true)
	}
	Rule(LogicalElement head, IElement body, boolean doChecks) {
		this.head = head
		this.body = body
		this.isDirective = (
				body == null &&
				head.elements.size() == 1 &&
				head.elements.first() instanceof Directive)
		this._loc = SourceManager.v().getLastLoc()

		if (doChecks && body != null) {
			def varsInHead = head.getVars()
			def varsInBody = body.getVars()
			varsInBody.findAll{ v -> !v.isDontCare }
			          .findAll{ v -> !varsInHead.contains(v) }
			          .findAll{ v -> Collections.frequency(varsInBody, v) == 1 }
			          .each{ v -> ErrorManager.warn(ErrorId.UNUSED_VAR, v.name) }
		}
	}

	Directive getDirective() {
		return (isDirective ? (Directive) head.elements.first() : null)
	}


	@Override
	<T> T accept(IVisitor<T> v) { return v.visit(this) }

	String toString() { return "$head <- $body." }

	SourceLocation _loc
	SourceLocation location() { return _loc }
}
