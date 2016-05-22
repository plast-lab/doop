package deepdoop.datalog;

interface IAtom extends IElement {

	enum Type { PREDICATE, FUNCTIONAL, REFMODE }

	String name();
	Type   type();
	int    arity();

	IAtom init(String id);
}
