package org.clyze.deepdoop.datalog.expr

import groovy.transform.Canonical
import org.clyze.deepdoop.actions.IVisitor

@Canonical
class VariableExpr implements IExpr {

	String name

	def isDontCare() { name == '_' }

	List<VariableExpr> getVars() { [this] }

	def <T> T accept(IVisitor<T> v) { v.visit(this)  }

	static List<VariableExpr> genTempVars(int n) {
		(0..<n).collect{ new VariableExpr("var$it") }
	}
}
