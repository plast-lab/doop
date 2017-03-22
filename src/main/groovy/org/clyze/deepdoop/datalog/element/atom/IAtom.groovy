package org.clyze.deepdoop.datalog.element.atom

import org.clyze.deepdoop.datalog.element.IElement
import org.clyze.deepdoop.datalog.expr.VariableExpr

interface IAtom extends IElement {
	String name()
	String stage()
	int    arity()
	IAtom  newAtom(String stage, List<VariableExpr> vars)
	IAtom  newAlias(String name, String stage, List<VariableExpr> vars)
}