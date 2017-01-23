package org.clyze.deepdoop.datalog.expr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import org.clyze.deepdoop.actions.IVisitor;

public class VariableExpr implements IExpr {

	public final String  name;
	public final boolean isDontCare;

	public VariableExpr(String name) {
		this.name       = name;
		this.isDontCare = "_".equals(name);
	}

	@Override
	public List<VariableExpr> getVars() {
		return Arrays.asList(this);
	}
	@Override
	public <T> T accept(IVisitor<T> v) {
		return v.visit(this);
	}
	@Override
	public boolean equals(Object o) {
		return (o instanceof VariableExpr) && ((VariableExpr)o).name.equals(name);
	}
	@Override
	public int hashCode() {
		return name.hashCode();
	}


	public static List<VariableExpr> genTempVars(int n) {
		List<VariableExpr> vars = new ArrayList<>(n);
		IntStream.range(0, n).forEach(i -> vars.add(new VariableExpr("var" + i)));
		return vars;
	}


	@Override
	public String toString() {
		return name;
	}
}
