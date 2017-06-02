package org.clyze.deepdoop.datalog.element.atom

import groovy.transform.Canonical
import org.clyze.deepdoop.actions.IVisitor
import org.clyze.deepdoop.datalog.expr.VariableExpr

@Canonical
class Primitive implements IAtom {

	String name
	VariableExpr var

	String getStage() { null }

	int getArity() { 1 }

	IAtom newAtom(String stage, List<VariableExpr> vars) {
		assert arity == vars.size()
		return this
	}

	IAtom newAlias(String name, String stage, List<VariableExpr> vars) {
		throw new UnsupportedOperationException()
	}

	def <T> T accept(IVisitor<T> v) { v.visit(this) }

	static boolean isPrimitive(String name) {
		switch (name) {
			case "int":
			case "float":
			case "boolean":
			case "string":
				return true
			default:
				return false
		}
	}
}
