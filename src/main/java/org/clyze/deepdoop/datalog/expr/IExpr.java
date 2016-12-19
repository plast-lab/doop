package org.clyze.deepdoop.datalog.expr;

import java.util.List;
import org.clyze.deepdoop.actions.IVisitable;

public interface IExpr extends IVisitable {
	List<VariableExpr> getVars();
}
