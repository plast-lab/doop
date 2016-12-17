package org.clyze.deepdoop.datalog.element.atom;

import java.util.Arrays;
import java.util.List;
import org.clyze.deepdoop.actions.IVisitor;
import org.clyze.deepdoop.datalog.expr.*;

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
	public String name() { return name; }
	@Override
	public String stage() { return null; }
	@Override
	public int arity() { return 1; }
	@Override
	public IAtom instantiate(String stage, List<VariableExpr> vars) {
		assert arity() == vars.size();
		return this;
	}
	@Override
	public List<VariableExpr> getVars() {
		return Arrays.asList(var);
	}
	@Override
	public <T> T accept(IVisitor<T> v) {
		return v.visit(this);
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
