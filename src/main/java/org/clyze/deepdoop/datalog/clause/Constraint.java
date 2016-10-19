package org.clyze.deepdoop.datalog.clause;

import org.clyze.deepdoop.actions.IVisitable;
import org.clyze.deepdoop.actions.IVisitor;
import org.clyze.deepdoop.datalog.element.IElement;

public class Constraint implements IVisitable {

	public final IElement head;
	public final IElement body;

	public Constraint(IElement head, IElement body) {
		this.head = head;
		this.body = body;
	}


	@Override
	public <T> T accept(IVisitor<T> v) {
		return v.visit(this);
	}
}
