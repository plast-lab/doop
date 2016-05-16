package deepdoop.datalog;

interface IAtom extends IInitializable<IAtom> {
	String name();
	int arity();
}
