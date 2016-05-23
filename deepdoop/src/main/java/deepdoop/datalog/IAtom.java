package deepdoop.datalog;

import java.util.List;

interface IAtom extends IElement {

	enum Type { PREDICATE, FUNCTIONAL, REFMODE }

	String             name();
	Type               type();
	int                arity();
	default List<VariableExpr> getVars() { return null; }

	IAtom              init(String id);
}
