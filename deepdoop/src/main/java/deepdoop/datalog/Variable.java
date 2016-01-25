package deepdoop.datalog;

public class Variable {

	static Variable _empty;

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

	public static Variable emptyVariable() {
		if (_empty == null) _empty = new Variable();
		return _empty;
	}

	@Override
	public String toString() {
		return name;
	}
}
