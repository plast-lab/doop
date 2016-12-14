package org.clyze.deepdoop.datalog.clause;

import org.clyze.deepdoop.actions.IVisitable;
import org.clyze.deepdoop.actions.IVisitor;
import org.clyze.deepdoop.datalog.element.IElement;
import org.clyze.deepdoop.datalog.element.LogicalElement;
import org.clyze.deepdoop.datalog.element.atom.Directive;
import org.clyze.deepdoop.system.ISourceItem;
import org.clyze.deepdoop.system.SourceLocation;

public class Rule implements IVisitable, ISourceItem {

	public final LogicalElement head;
	public final IElement       body;
	public final boolean        isDirective;
	SourceLocation              _loc;

	public Rule(LogicalElement head, IElement body) {
		this(head, body, null);
	}
	public Rule(LogicalElement head, IElement body, SourceLocation loc) {
		this.head = head;
		this.body = body;

		this.isDirective = (
				body == null &&
				head.elements.size() == 1 &&
				head.elements.iterator().next() instanceof Directive);
		this._loc = loc;
	}

	public Directive getDirective() {
		return (isDirective ? (Directive) head.elements.iterator().next() : null);
	}


	@Override
	public <T> T accept(IVisitor<T> v) {
		return v.visit(this);
	}
	@Override
	public SourceLocation location() {
		return _loc;
	}
}
