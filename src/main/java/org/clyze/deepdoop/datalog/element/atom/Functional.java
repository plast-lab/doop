package org.clyze.deepdoop.datalog.element.atom;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import org.clyze.deepdoop.actions.IVisitor;
import org.clyze.deepdoop.datalog.expr.*;

public class Functional implements IAtom {

	public final String      name;
	public final String      stage;
	public final List<IExpr> keyExprs;
	public final IExpr       valueExpr;

	public Functional(String name, String stage, List<IExpr> keyExprs, IExpr valueExpr) {
		this.name      = name;
		this.stage     = stage;
		this.keyExprs  = keyExprs;
		this.valueExpr = valueExpr;
	}

	@Override
	public String name() { return name; }
	@Override
	public String stage() { return stage; }
	@Override
	public int arity() { return keyExprs.size() + 1; }
	@Override
	public IAtom instantiate(String stage, List<VariableExpr> vars) {
		assert arity() == vars.size();
		VariableExpr valueVar = (VariableExpr) vars.remove(vars.size()-1);
		return new Functional(name, stage, new ArrayList<>(vars), valueVar);
	}
	@Override
	public List<VariableExpr> getVars() {
		List<VariableExpr> list = new ArrayList<>();
		keyExprs.forEach(e -> list.addAll(e.getVars()));
		if (valueExpr != null) list.addAll(valueExpr.getVars());
		return list;
	}
	@Override
	public <T> T accept(IVisitor<T> v) {
		return v.visit(this);
	}


	@Override
	public String toString() {
		StringJoiner joiner = new StringJoiner(", ");
		keyExprs.forEach(e -> joiner.add(e.toString()));
		return name + "[" + joiner + "]" + (valueExpr == null ? "" : " = " + valueExpr);
	}
}
