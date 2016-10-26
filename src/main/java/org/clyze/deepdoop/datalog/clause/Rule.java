package org.clyze.deepdoop.datalog.clause;

import org.clyze.deepdoop.actions.IVisitable;
import org.clyze.deepdoop.actions.IVisitor;
import org.clyze.deepdoop.datalog.element.IElement;
import org.clyze.deepdoop.datalog.element.LogicalElement;
import org.clyze.deepdoop.datalog.element.atom.Directive;

public class Rule implements IVisitable {

	public final LogicalElement head;
	public final IElement       body;
	public final boolean        isDirective;

	public Rule(LogicalElement head, IElement body) {
		this.head = head;
		this.body = body;

		this.isDirective = (
				body == null &&
				head.elements.size() == 1 &&
				head.elements.iterator().next() instanceof Directive);
	}

	public Directive getDirective() {
		return (isDirective ? (Directive) head.elements.iterator().next() : null);
	}


	@Override
	public <T> T accept(IVisitor<T> v) {
		return v.visit(this);
	}
}
