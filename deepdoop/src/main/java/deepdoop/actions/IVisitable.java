package deepdoop.actions;

public interface IVisitable {
	// Enabling double dispatch
	<T> T accept (IVisitor<T> v);
}
