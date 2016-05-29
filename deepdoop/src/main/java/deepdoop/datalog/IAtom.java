package deepdoop.datalog;

import java.util.List;

interface IAtom extends IElement {
	String             name();
	String             stage();
	int                arity();
	List<IExpr>        getExprs();
	List<VariableExpr> getExprsAsVars();
	IAtom              init(Initializer ini);
}
