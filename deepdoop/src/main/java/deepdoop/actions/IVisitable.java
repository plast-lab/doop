package deepdoop.actions;

public interface IVisitable {
	IVisitable accept (IVisitor v);
}