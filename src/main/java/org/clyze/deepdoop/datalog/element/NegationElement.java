package org.clyze.deepdoop.datalog.element;

import java.util.List;
import org.clyze.deepdoop.actions.IVisitor;
import org.clyze.deepdoop.datalog.expr.*;
import org.clyze.deepdoop.system.*;

public class NegationElement implements IElement {

	public final IElement element;

	public NegationElement(IElement element) {
		this.element = element;
		this._loc    = SourceManager.v().getLastLoc();
	}


	@Override
	public List<VariableExpr> getVars() {
		return element.getVars();
	}
	@Override
	public <T> T accept(IVisitor<T> v) {
		return v.visit(this);
	}

	SourceLocation _loc;
	@Override
	public SourceLocation location() { return _loc; }
}
