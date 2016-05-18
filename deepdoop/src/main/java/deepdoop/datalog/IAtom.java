package deepdoop.datalog;

interface IAtom extends IInitializable<IAtom> {

	enum Type { PREDICATE, FUNCTIONAL, REFMODE }

	String name();
	Type   type();
	int    arity();
}
