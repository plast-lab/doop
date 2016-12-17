package org.clyze.deepdoop.datalog.expr;

import java.util.List;
import org.clyze.deepdoop.actions.IVisitable;
import org.clyze.deepdoop.system.ISourceItem;

public interface IExpr extends IVisitable, ISourceItem {
	List<VariableExpr> getVars();
}
