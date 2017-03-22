package org.clyze.deepdoop.datalog.element

import org.clyze.deepdoop.actions.IVisitable
import org.clyze.deepdoop.datalog.expr.VariableExpr
import org.clyze.deepdoop.system.ISourceItem

interface IElement extends IVisitable, ISourceItem {
	List<VariableExpr> getVars()
}