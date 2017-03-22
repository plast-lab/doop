package org.clyze.deepdoop.datalog.expr

import org.clyze.deepdoop.actions.IVisitor

class ConstantExpr implements IExpr {

	enum Type { INTEGER, REAL, BOOLEAN, STRING }

	Type   type
	Object value

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

	List<VariableExpr> getVars() { [] }

	def <T> T accept(IVisitor<T> v) { v.visit(this) }

	String toString() { value.toString() }
}