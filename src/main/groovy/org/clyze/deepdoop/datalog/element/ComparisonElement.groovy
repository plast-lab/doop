package org.clyze.deepdoop.datalog.element

import groovy.transform.Canonical
import org.clyze.deepdoop.actions.IVisitor
import org.clyze.deepdoop.datalog.BinOperator
import org.clyze.deepdoop.datalog.expr.*

@Canonical
class ComparisonElement implements IElement {

	@Delegate BinaryExpr expr

	ComparisonElement(IExpr left, BinOperator op, IExpr right) {
		this.expr = new BinaryExpr(left, op, right)
	}
	ComparisonElement(BinaryExpr expr) {
		this.expr = expr
	}

	def <T> T accept(IVisitor<T> v) { v.visit(this) }
}
