package org.clyze.deepdoop.datalog.element;

import org.clyze.deepdoop.actions.IVisitor;
import org.clyze.deepdoop.datalog.element.atom.Predicate;
import org.clyze.deepdoop.datalog.expr.VariableExpr;

public class AggregationElement implements IElement {

	public final VariableExpr var;
	public final Predicate    predicate;
	public final IElement     body;

	public AggregationElement(VariableExpr var, Predicate predicate, IElement body) {
		this.var       = var;
		this.predicate = predicate;
		this.body      = body;
	}


	@Override
	public <T> T accept(IVisitor<T> v) {
		return v.visit(this);
	}
}
