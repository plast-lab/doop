package org.clyze.deepdoop.datalog.element.atom

import org.clyze.deepdoop.actions.IVisitor
import org.clyze.deepdoop.datalog.expr.*
import org.clyze.deepdoop.system.*

class Primitive implements IAtom {

	String       name
	int          capacity
	VariableExpr var

	Primitive(String name, String cap, VariableExpr var) {
		this.capacity = normalize(name, cap)
		this.name     = name + (this.capacity != 0 ? "[${this.capacity}]" : "")
		this.var      = var
	}

	String name() { name }
	String stage() { null }
	int arity() { 1 }
	IAtom newAtom(String stage, List<VariableExpr> vars) {
		assert arity() == vars.size()
		return this
	}
	IAtom newAlias(String name, String stage, List<VariableExpr> vars) {
		throw new UnsupportedOperationException()
	}
	List<VariableExpr> getVars() { [var] }

	def <T> T accept(IVisitor<T> v) { v.visit(this) }

	String toString() { "$name($var)" }

	SourceLocation location() { null }


	static int normalize(String name, String capacity) {
		switch (name) {
			case "uint":
			case "int":
			case "float":
			case "decimal":
				// capacity as a string is wrapped in square brackets
				return capacity == null ? 64 : Integer.parseInt(capacity.substring(1).substring(0, capacity.length()-2))
			default:
				return 0
		}
	}
}