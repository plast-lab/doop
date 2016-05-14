package deepdoop.datalog;

public class VariableExpr implements IExpr {

	public final String name;

	public VariableExpr(String name) {
		this.name = name;
	}

	@Override
	public IExpr init(String id) {
		return this;
	}

	@Override
	public String toString() {
		return name;
	}
}
