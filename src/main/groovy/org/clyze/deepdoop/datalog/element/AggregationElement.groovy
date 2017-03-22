package org.clyze.deepdoop.datalog.element

import org.clyze.deepdoop.actions.IVisitor
import org.clyze.deepdoop.datalog.element.atom.Predicate
import org.clyze.deepdoop.datalog.expr.*
import org.clyze.deepdoop.system.*

class AggregationElement implements IElement {

	VariableExpr var
	Predicate    predicate
	IElement     body

	AggregationElement(VariableExpr var, Predicate predicate, IElement body) {
		this.var       = var
		this.predicate = predicate
		this.body      = body
		this.loc       = SourceManager.v().getLastLoc()
	}

	List<VariableExpr> getVars() {
		body.getVars() + predicate.getVars() + [var]
	}

	def <T> T accept(IVisitor<T> v) { v.visit(this) }

	String toString() { "agg<<$var = $predicate>> $body" }

	SourceLocation loc
	SourceLocation location() { loc }
}