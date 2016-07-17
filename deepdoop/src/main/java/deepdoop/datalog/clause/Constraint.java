package deepdoop.datalog.clause;

import deepdoop.actions.IVisitable;
import deepdoop.actions.IVisitor;
import deepdoop.datalog.element.IElement;

public class Constraint implements IVisitable {

	public final IElement head;
	public final IElement body;

	public Constraint(IElement head, IElement body) {
		this.head = head;
		this.body = body;
	}

	@Override
	public String toString() {
		return head + " -> " + body + ".";
	}


	@Override
	public <T> T accept(IVisitor<T> v) {
		return v.visit(this);
	}
}
