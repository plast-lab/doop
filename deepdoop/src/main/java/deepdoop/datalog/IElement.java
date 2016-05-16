package deepdoop.datalog;

interface IElement extends IInitializable<IElement> {
	default void flatten() {}
}
