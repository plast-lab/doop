package org.clyze.deepdoop.datalog.element.atom

import org.clyze.deepdoop.actions.IVisitor
import org.clyze.deepdoop.datalog.expr.*
import org.clyze.deepdoop.system.*

// Special class for when only a string is actually present but we need to
// treat it as an atom object
class StubAtom implements IAtom {

	public final String name
	public final String stage

	StubAtom(String name) {
		this(name, null)
	}
	StubAtom(String name, String stage) {
		this.name  = name
		this.stage = stage
	}

	@Override
	String name() { return name }
	@Override
	String stage() { return stage }
	@Override
	int arity() { throw new UnsupportedOperationException() }
	@Override
	IAtom instantiate(String stage, List<VariableExpr> vars) { throw new UnsupportedOperationException() }
	@Override
	List<VariableExpr> getVars() { throw new UnsupportedOperationException() }
	@Override
	<T> T accept(IVisitor<T> v) { return v.visit(this) }

	String toString() { return "$name<stub>" }

	SourceLocation location() { return null }
}
