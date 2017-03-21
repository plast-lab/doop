package org.clyze.deepdoop.datalog.expr

import org.clyze.deepdoop.actions.IVisitor

class ConstantExpr implements IExpr {

	enum Type { INTEGER, REAL, BOOLEAN, STRING }

	public final Type   type
	public final Object value

	ConstantExpr(Long l) {
		type  = Type.INTEGER
		value = l
	}
	ConstantExpr(Double r) {
		type  = Type.REAL
		value = r
	}
	ConstantExpr(Boolean b) {
		type  = Type.BOOLEAN
		value = b
	}
	ConstantExpr(String s) {
		type  = Type.STRING
		value = s
	}


	@Override
	List<VariableExpr> getVars() { return new ArrayList<>() }
	@Override
	<T> T accept(IVisitor<T> v) { return v.visit(this) }

	String toString() { return value.toString() }
}
