package org.clyze.deepdoop.datalog.clause;

import org.clyze.deepdoop.actions.*;
import org.clyze.deepdoop.datalog.element.IElement;
import org.clyze.deepdoop.system.*;

public class Constraint implements IVisitable, ISourceItem {

	public final IElement head;
	public final IElement body;
	SourceLocation        _loc;

	public Constraint(IElement head, IElement body) {
		this(head, body, null);
	}
	public Constraint(IElement head, IElement body, SourceLocation loc) {
		this.head = head;
		this.body = body;
		this._loc = loc;
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
