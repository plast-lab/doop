package deepdoop.datalog.element;

import deepdoop.actions.IVisitor;

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
