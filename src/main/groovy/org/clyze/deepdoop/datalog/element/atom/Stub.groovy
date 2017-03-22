package org.clyze.deepdoop.datalog.element.atom

import org.clyze.deepdoop.actions.IVisitor
import org.clyze.deepdoop.datalog.expr.*
import org.clyze.deepdoop.system.*

// Special class for when only a string is actually present but we need to
// treat it as an atom object
class Stub implements IAtom {

	String name
	String stage

    Stub(String name) {
		this(name, null)
	}

    Stub(String name, String stage) {
		this.name  = name
		this.stage = stage
	}

	String name() { name }
	String stage() { stage }
	int arity() { throw new UnsupportedOperationException() }
	IAtom newAtom(String stage, List<VariableExpr> vars) { throw new UnsupportedOperationException() }
	IAtom newAlias(String name, String stage, List<VariableExpr> vars) { throw new UnsupportedOperationException() }
	List<VariableExpr> getVars() { throw new UnsupportedOperationException() }

	def <T> T accept(IVisitor<T> v) { v.visit(this) }

	String toString() { "$name<stub>" }

	SourceLocation location() { null }
}