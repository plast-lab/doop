package org.clyze.deepdoop.datalog.element;

import org.clyze.deepdoop.actions.IVisitor;

public class GroupElement implements IElement {

	public final IElement element;

	public GroupElement(IElement element) {
		this.element = element;
	}


	@Override
	public <T> T accept(IVisitor<T> v) {
		return v.visit(this);
	}
}
