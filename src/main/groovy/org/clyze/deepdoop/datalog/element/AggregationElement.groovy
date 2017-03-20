package org.clyze.deepdoop.datalog.element

import org.clyze.deepdoop.actions.IVisitor
import org.clyze.deepdoop.datalog.element.atom.Predicate
import org.clyze.deepdoop.datalog.expr.*
import org.clyze.deepdoop.system.*

class AggregationElement implements IElement {

	public final VariableExpr var
	public final Predicate    predicate
	public final IElement     body

	AggregationElement(VariableExpr var, Predicate predicate, IElement body) {
		this.var       = var
		this.predicate = predicate
		this.body      = body
		this._loc      = SourceManager.v().getLastLoc()
	}


	@Override
	List<VariableExpr> getVars() {
		return body.getVars() + predicate.getVars() + [var]
	}
	@Override
	<T> T accept(IVisitor<T> v) { return v.visit(this) }

	String toString() { return "agg<<$var = $predicate>> $body" }

	SourceLocation _loc
	SourceLocation location() { return _loc }
}
