package deepdoop.datalog;

interface IElement extends IInitializable<IElement>, IAtomContainer {
	default void flatten() {}
}
