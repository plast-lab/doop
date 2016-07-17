package deepdoop.datalog.element;

import deepdoop.actions.IVisitor;

public class GroupElement implements IElement {

	public final IElement element;

	public GroupElement(IElement element) {
		this.element = element;
	}

	@Override
	public String toString() {
		return "(" + element + ")";
	}


	@Override
	public <T> T accept(IVisitor<T> v) {
		return v.visit(this);
	}
}
