package org.clyze.deepdoop.datalog.element;

import java.util.List;
import org.clyze.deepdoop.actions.IVisitable;
import org.clyze.deepdoop.datalog.expr.VariableExpr;
import org.clyze.deepdoop.system.*;

public interface IElement extends IVisitable, ISourceItem {
	List<VariableExpr> getVars();
}
