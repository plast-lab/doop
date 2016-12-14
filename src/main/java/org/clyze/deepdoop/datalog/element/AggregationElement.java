package org.clyze.deepdoop.datalog.element;

import org.clyze.deepdoop.actions.IVisitor;
import org.clyze.deepdoop.datalog.element.atom.Predicate;
import org.clyze.deepdoop.datalog.expr.VariableExpr;
import org.clyze.deepdoop.system.SourceLocation;

public class AggregationElement implements IElement {

	public final VariableExpr var;
	public final Predicate    predicate;
	public final IElement     body;
	SourceLocation            _loc;

	public AggregationElement(VariableExpr var, Predicate predicate, IElement body) {
		this(var, predicate, body, null);
	}
	public AggregationElement(VariableExpr var, Predicate predicate, IElement body, SourceLocation loc) {
		this.var       = var;
		this.predicate = predicate;
		this.body      = body;
		this._loc      = loc;
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
