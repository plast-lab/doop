package org.clyze.deepdoop.datalog.element.atom

import org.clyze.deepdoop.datalog.element.IElement
import org.clyze.deepdoop.datalog.expr.VariableExpr

interface IAtom extends IElement {
	String name()
	String stage()
	int    arity()
	IAtom  instantiate(String stage, List<VariableExpr> vars)
}
