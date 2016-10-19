package org.clyze.deepdoop.datalog.element.atom;

import java.util.List;
import org.clyze.deepdoop.datalog.element.IElement;
import org.clyze.deepdoop.datalog.expr.VariableExpr;

public interface IAtom extends IElement {
	String             name();
	String             stage();
	int                arity();
	List<VariableExpr> getVars();
	IAtom              instantiate(String stage, List<VariableExpr> vars);
}
