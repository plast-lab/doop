package org.clyze.deepdoop.datalog.element;

import java.util.ArrayList;
import java.util.List;
import org.clyze.deepdoop.actions.IVisitor;
import org.clyze.deepdoop.datalog.element.atom.Predicate;
import org.clyze.deepdoop.datalog.expr.*;
import org.clyze.deepdoop.system.*;

public class AggregationElement implements IElement {

	public final VariableExpr var;
	public final Predicate    predicate;
	public final IElement     body;

	public AggregationElement(VariableExpr var, Predicate predicate, IElement body) {
		this.var       = var;
		this.predicate = predicate;
		this.body      = body;
		this._loc      = SourceManager.v().getLastLoc();
	}


	@Override
	public List<VariableExpr> getVars() {
		List<VariableExpr> list = new ArrayList<>(body.getVars());
		list.add(var);
		return list;
	}
	@Override
	public <T> T accept(IVisitor<T> v) {
		return v.visit(this);
	}

	SourceLocation _loc;
	@Override
	public SourceLocation location() { return _loc; }
}
