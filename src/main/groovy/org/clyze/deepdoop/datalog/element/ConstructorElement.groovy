package org.clyze.deepdoop.datalog.element

import org.clyze.deepdoop.actions.IVisitor
import org.clyze.deepdoop.datalog.element.atom.Predicate
import org.clyze.deepdoop.datalog.expr.*
import org.clyze.deepdoop.datalog.element.atom.*
import org.clyze.deepdoop.system.*

class ConstructorElement implements IElement {

	Functional constructor
	IAtom      type

	ConstructorElement(Predicate constructor, IAtom type) {
		this.constructor = constructor as Functional
		this.type        = type
		this.loc         = SourceManager.v().getLastLoc()
	}

	List<VariableExpr> getVars() {
		constructor.getVars()
	}

	def <T> T accept(IVisitor<T> v) { v.visit(this) }

	String toString() { "new<<$constructor as ${type.name()}>>" }

	SourceLocation loc
	SourceLocation location() { loc }
}
