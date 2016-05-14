package deepdoop.datalog;

interface IElement {
	default void flatten() {}
	IElement init(String id);
}
