package org.clyze.deepdoop.datalog.element;

import org.clyze.deepdoop.actions.IVisitor;

public class NegationElement implements IElement {

	public final IElement element;

	public NegationElement(IElement element) {
		this.element = element;
	}


	@Override
	public <T> T accept(IVisitor<T> v) {
		return v.visit(this);
	}
}
