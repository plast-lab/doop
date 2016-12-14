package org.clyze.deepdoop.datalog.element;

import org.clyze.deepdoop.actions.IVisitor;
import org.clyze.deepdoop.system.SourceLocation;

public class GroupElement implements IElement {

	public final IElement element;
	SourceLocation        _loc;

	public GroupElement(IElement element) {
		this(element, null);
	}
	public GroupElement(IElement element, SourceLocation loc) {
		this.element = element;
		this._loc    = loc;
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
