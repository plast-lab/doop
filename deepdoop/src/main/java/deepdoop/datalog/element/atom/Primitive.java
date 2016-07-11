package deepdoop.datalog.element.atom;

import deepdoop.actions.IVisitable;
import deepdoop.actions.IVisitor;
import deepdoop.datalog.expr.VariableExpr;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class Primitive implements IAtom {

	public final String       name;
	public final int          capacity;
	public final VariableExpr var;

	public Primitive(String name, String cap, VariableExpr var) {
		this.capacity = normalize(name, cap);
		this.name     = name + (this.capacity != 0 ? "[" + this.capacity + "]" : "");
		this.var = var;
	}

	@Override
	public IVisitable accept(IVisitor v) {
		v.enter(this);
		Map<IVisitable, IVisitable> m =
			Collections.singletonMap(var, var.accept(v));
		return v.exit(this, m);
	}

	@Override
	public String name() { return name; }
	@Override
	public String stage() { return null; }
	@Override
	public int arity() { return 1; }
	@Override
	public List<VariableExpr> getVars() {
		return Collections.singletonList(var);
	}
	@Override
	public IAtom instantiate(String stage, List<VariableExpr> vars) { return this; }

	@Override
	public String toString() {
		return name + "(" + var + ")";
	}


	static int normalize(String name, String capacity) {
		switch (name) {
			case "uint":
			case "int":
			case "float":
			case "decimal":
				// capacity as a string is wrapped in square brackets
				return capacity == null ? 64 : Integer.parseInt(capacity.substring(1).substring(0, capacity.length()-2));
			default:
				return 0;
		}
	}
}
