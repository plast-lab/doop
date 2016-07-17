package deepdoop.datalog.clause;

import deepdoop.actions.IVisitable;
import deepdoop.actions.IVisitor;
import deepdoop.datalog.element.IElement;
import deepdoop.datalog.element.LogicalElement;
import deepdoop.datalog.element.atom.Directive;

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
	public String toString() {
		return head + (body != null ? " <- " + body : "") + ".";
	}


	@Override
	public <T> T accept(IVisitor<T> v) {
		return v.visit(this);
	}
}
