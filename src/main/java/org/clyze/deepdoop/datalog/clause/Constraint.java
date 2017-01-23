package org.clyze.deepdoop.datalog.clause;

import org.clyze.deepdoop.actions.*;
import org.clyze.deepdoop.datalog.element.IElement;
import org.clyze.deepdoop.system.*;

public class Constraint implements IVisitable, ISourceItem {

	public final IElement head;
	public final IElement body;

	public Constraint(IElement head, IElement body) {
		this.head = head;
		this.body = body;
		this._loc = SourceManager.v().getLastLoc();
	}


	@Override
	public <T> T accept(IVisitor<T> v) {
		return v.visit(this);
	}

	SourceLocation _loc;
	@Override
	public SourceLocation location() { return _loc; }


	@Override
	public String toString() {
		return head + " -> " + body + ".";
	}
}
