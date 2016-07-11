package deepdoop.datalog.element.atom;

import deepdoop.datalog.element.IElement;
import deepdoop.datalog.expr.VariableExpr;
import java.util.List;

public interface IAtom extends IElement {
	String             name();
	String             stage();
	int                arity();
	List<VariableExpr> getVars();
	IAtom              instantiate(String stage, List<VariableExpr> vars);
}
