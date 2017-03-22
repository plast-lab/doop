package org.clyze.deepdoop.datalog.expr

import org.clyze.deepdoop.actions.IVisitor

class VariableExpr implements IExpr {

	String  name
	boolean isDontCare

	VariableExpr(String name) {
		this.name       = name
		this.isDontCare = (name == '_')
	}

	List<VariableExpr> getVars() { [this] }

	def <T> T accept(IVisitor<T> v) { v.visit(this)  }

	boolean equals(Object o) {
		return (o instanceof VariableExpr) && (o as VariableExpr).name == name
	}

	int hashCode() { name.hashCode() }

	String toString() { name }


	static List<VariableExpr> genTempVars(int n) {
		(0..n-1).collect{ new VariableExpr("var$it") }
	}
}