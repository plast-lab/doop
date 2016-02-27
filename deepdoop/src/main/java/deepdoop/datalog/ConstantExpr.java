package deepdoop.datalog;

public class ConstantExpr implements IExpr {

	public enum Type { INTEGER, REAL, BOOLEAN, STRING }

	public final Type type;
	public final Object value;

	public ConstantExpr(Integer i) {
		type = Type.INTEGER;
		value = i;
	}
	public ConstantExpr(Double r) {
		type = Type.REAL;
		value = r;
	}
	public ConstantExpr(Boolean b) {
		type = Type.BOOLEAN;
		value = b;
	}
	public ConstantExpr(String s) {
		type = Type.STRING;
		value = s;
	}

	@Override
	public String toString() {
		return value.toString();
	}
}
