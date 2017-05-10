package org.clyze.deepdoop.datalog.clause

import groovy.transform.Canonical
import org.clyze.deepdoop.actions.*
import org.clyze.deepdoop.datalog.element.*
import org.clyze.deepdoop.datalog.element.atom.*
import org.clyze.deepdoop.system.*

@Canonical
class Rule implements IVisitable, TSourceItem {

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

		if (doChecks && body != null) {
			def varsInHead = head.getVars()
			def varsInBody = body.getVars()
			varsInBody.findAll{ !it.isDontCare() }
			          .findAll{ !varsInHead.contains(it) }
			          .findAll{ Collections.frequency(varsInBody, it) == 1 }
			          .each{ ErrorManager.warn(ErrorId.UNUSED_VAR, it.name) }
		}
	}

	Directive getDirective() {
		isDirective ? head.elements.first() as Directive : null
	}

	def <T> T accept(IVisitor<T> v) { v.visit(this) }
}
