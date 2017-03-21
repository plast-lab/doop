package org.clyze.deepdoop.datalog.element.atom

import org.clyze.deepdoop.actions.IVisitor
import org.clyze.deepdoop.datalog.expr.*
import org.clyze.deepdoop.system.*

class Primitive implements IAtom {

	public final String       name
	public final int          capacity
	public final VariableExpr var

	Primitive(String name, String cap, VariableExpr var) {
		this.capacity = normalize(name, cap)
		this.name     = name + (this.capacity != 0 ? "[${this.capacity}]" : "")
		this.var      = var
	}

	@Override
	String name() { return name }
	@Override
	String stage() { return null }
	@Override
	int arity() { return 1 }
	@Override
	IAtom instantiate(String stage, List<VariableExpr> vars) {
		assert arity() == vars.size()
		return this
	}
	@Override
	List<VariableExpr> getVars() { return [var] }
	@Override
	<T> T accept(IVisitor<T> v) { return v.visit(this) }

	String toString() { return "$name($var)" }

	SourceLocation location() { return null }


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
