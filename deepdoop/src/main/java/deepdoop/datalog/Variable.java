package deepdoop.datalog;

public class Variable {

	public final String name;
	public final boolean isEmpty;

	Variable() {
		this.name = "";
		this.isEmpty = true;
	}
	public Variable(String name) {
		this.name = name;
		this.isEmpty = false;
	}

	@Override
	public String toString() {
		return name;
	}
}
