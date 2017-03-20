package org.clyze.deepdoop.datalog.expr

import org.clyze.deepdoop.actions.IVisitor

class VariableExpr implements IExpr {

	public final String  name
	public final boolean isDontCare

	VariableExpr(String name) {
		this.name       = name
		this.isDontCare = (name == '_')
	}

	@Override
	List<VariableExpr> getVars() { return [this] }
	@Override
	<T> T accept(IVisitor<T> v) { return v.visit(this)  }

	boolean equals(Object o) {
		return (o instanceof VariableExpr) && (o as VariableExpr).name == name
	}

	int hashCode() { return name.hashCode() }

	String toString() { return name }


	static List<VariableExpr> genTempVars(int n) {
		return (0..n-1).collect{ i -> new VariableExpr("var$i") }
	}
}
