package org.clyze.deepdoop.datalog.clause

import org.clyze.deepdoop.actions.*
import org.clyze.deepdoop.datalog.element.*
import org.clyze.deepdoop.datalog.element.atom.*
import org.clyze.deepdoop.system.*

class Rule implements IVisitable, ISourceItem {

	LogicalElement head
	IElement       body
	boolean        isDirective

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
		this.loc  = SourceManager.v().getLastLoc()

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
		isDirective ? head.elements.first() as Directive : null
	}

	def <T> T accept(IVisitor<T> v) { v.visit(this) }

	String toString() { "$head <- $body." }

	SourceLocation loc
	SourceLocation location() { loc }
}