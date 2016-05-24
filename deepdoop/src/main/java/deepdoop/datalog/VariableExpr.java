package deepdoop.datalog;

public class VariableExpr implements IExpr {

	public final String name;

	public VariableExpr(String name) {
		this.name = name;
	}

	@Override
	public VariableExpr init(Initializer ini) {
		return this;
	}

	@Override
	public String toString() {
		return name;
	}

	@Override
	public boolean equals(Object o) {
		return (o instanceof VariableExpr) && ((VariableExpr)o).name.equals(name);
	}

	@Override
	public int hashCode() {
		return name.hashCode();
	}
}
