package org.clyze.deepdoop.datalog.element.atom

import groovy.transform.Canonical
import org.clyze.deepdoop.actions.IVisitor
import org.clyze.deepdoop.datalog.expr.ConstantExpr
import org.clyze.deepdoop.datalog.expr.VariableExpr

@Canonical
class Directive implements IAtom {

	String name
	Stub backtick
	ConstantExpr constant
	boolean isPredicate
	int arity

	Directive(String name, Stub backtick) {
		assert backtick != null
		this.name = name
		this.backtick = backtick
		this.constant = null
		this.isPredicate = true
		this.arity = 1
	}

	Directive(String name, Stub backtick, ConstantExpr constant) {
		this.name = name
		this.backtick = backtick
		this.constant = constant
		this.isPredicate = false
		arity = (backtick == null ? 1 : 2)
	}

	String getStage() { null }

	IAtom newAtom(String stage, List<VariableExpr> vars) {
		assert arity == vars.size()
		this
	}

	IAtom newAlias(String name, String stage, List<VariableExpr> vars) {
		throw new UnsupportedOperationException()
	}

	List<VariableExpr> getVars() { [] }

	def <T> T accept(IVisitor<T> v) { v.visit(this) }
}
