package org.clyze.deepdoop.datalog.element.atom

import org.clyze.deepdoop.actions.IVisitor
import org.clyze.deepdoop.datalog.expr.*
import org.clyze.deepdoop.system.*

class Directive implements IAtom {

	public final String       name
	public final StubAtom     backtick
	public final ConstantExpr constant
	public final boolean      isPredicate
	int                       _arity

	Directive(String name, StubAtom backtick) {
		assert backtick != null
		this.name         = name
		this.backtick     = backtick
		this.constant     = null
		this.isPredicate  = true
		this._arity       = 1
	}
	Directive(String name, StubAtom backtick, ConstantExpr constant) {
		this.name         = name
		this.backtick     = backtick
		this.constant     = constant
		this.isPredicate  = false
		_arity            = (backtick == null ? 1 : 2)
	}

	@Override
	String name() { return name }
	@Override
	String stage() { return null }
	@Override
	int arity() { return _arity }
	@Override
	IAtom instantiate(String stage, List<VariableExpr> vars) {
		assert arity() == vars.size()
		return this
	}
	@Override
	List<VariableExpr> getVars() { return [] }
	@Override
	<T> T accept(IVisitor<T> v) { return v.visit(this) }

	String toString() {
		if (isPredicate)
			return "$name($backtick)"
		else
			return "$name[" + (backtick == null ? "" : backtick) + "] = $constant"
	}

	SourceLocation location() { return null }
}
