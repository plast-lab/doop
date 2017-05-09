package org.clyze.deepdoop.datalog.element

import org.clyze.deepdoop.actions.IVisitable
import org.clyze.deepdoop.datalog.expr.VariableExpr
import org.clyze.deepdoop.system.TSourceItem

interface IElement extends IVisitable, TSourceItem {
	List<VariableExpr> getVars()
}
