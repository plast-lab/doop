package org.clyze.deepdoop.datalog.element

import org.clyze.deepdoop.actions.IVisitor
import org.clyze.deepdoop.datalog.BinOperator
import org.clyze.deepdoop.datalog.expr.*
import org.clyze.deepdoop.system.*

class ComparisonElement implements IElement {

	BinaryExpr expr

	ComparisonElement(IExpr left, BinOperator op, IExpr right) {
		this.expr = new BinaryExpr(left, op, right)
		this.loc  = SourceManager.v().getLastLoc()
	}
	ComparisonElement(BinaryExpr expr) {
		this.expr = expr
		this.loc  = SourceManager.v().getLastLoc()
	}

	List<VariableExpr> getVars() { expr.getVars() }

	def <T> T accept(IVisitor<T> v) { v.visit(this) }

	String toString() { expr.toString() }

	SourceLocation loc
	SourceLocation location() { loc }
}