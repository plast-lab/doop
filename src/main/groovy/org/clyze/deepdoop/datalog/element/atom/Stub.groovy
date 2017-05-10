package org.clyze.deepdoop.datalog.element.atom

import groovy.transform.Canonical
import org.clyze.deepdoop.actions.IVisitor
import org.clyze.deepdoop.datalog.expr.VariableExpr

// Special class for when only a string is actually present but we need to
// treat it as an atom object
@Canonical
class Stub implements IAtom {

	String name
	String stage

	int getArity() { throw new UnsupportedOperationException() }
	IAtom newAtom(String stage, List<VariableExpr> vars) { throw new UnsupportedOperationException() }
	IAtom newAlias(String name, String stage, List<VariableExpr> vars) { throw new UnsupportedOperationException() }
	List<VariableExpr> getVars() { throw new UnsupportedOperationException() }

	def <T> T accept(IVisitor<T> v) { v.visit(this) }
}
