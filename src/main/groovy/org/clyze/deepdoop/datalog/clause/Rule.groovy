package org.clyze.deepdoop.datalog.clause

import groovy.transform.Canonical
import org.clyze.deepdoop.actions.*
import org.clyze.deepdoop.datalog.element.*
import org.clyze.deepdoop.datalog.element.LogicalElement.LogicType
import org.clyze.deepdoop.datalog.element.atom.*
import org.clyze.deepdoop.system.*
//import static org.clyze.deepdoop.datalog.element.LogicalElement.LogicType.*

@Canonical
class Rule implements IVisitable, TSourceItem {

	LogicalElement head
	LogicalElement body
	boolean        isDirective

	Rule(LogicalElement head, LogicalElement body, boolean doChecks) {
		this.head = head
		this.body = body
		this.isDirective = (
				body == null &&
				head.elements.size() == 1 &&
				head.elements.first() instanceof Directive)

		if (doChecks && body != null) {
			def varsInHead = head.vars
			def varsInBody = body.vars
			varsInBody.findAll{ !it.isDontCare() }
			          .findAll{ !varsInHead.contains(it) }
			          .findAll{ Collections.frequency(varsInBody, it) == 1 }
			          .each{ ErrorManager.warn(ErrorId.UNUSED_VAR, it.name) }
		}
	}
	Rule(LogicalElement head, LogicalElement body) {
		this(head, body, true)
	}

	Directive getDirective() {
		isDirective ? head.elements.first() as Directive : null
	}

	def <T> T accept(IVisitor<T> v) { v.visit(this) }
}
