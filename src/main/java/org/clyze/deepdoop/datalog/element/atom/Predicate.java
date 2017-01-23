package org.clyze.deepdoop.datalog.element.atom;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import org.clyze.deepdoop.actions.IVisitor;
import org.clyze.deepdoop.datalog.expr.*;

public class Predicate implements IAtom {

	public final String      name;
	public final String      stage;
	public final List<IExpr> exprs;

	public Predicate(String name, String stage, List<IExpr> exprs) {
		this.name  = name;
		this.stage = stage;
		this.exprs = exprs;
	}
	public Predicate(String name, List<IExpr> exprs) {
		this(name, null, exprs);
	}

	@Override
	public String name() { return name; }
	@Override
	public String stage() { return stage; }
	@Override
	public int arity() { return exprs.size(); }
	@Override
	public IAtom instantiate(String stage, List<VariableExpr> vars) {
		assert arity() == vars.size();
		return new Predicate(name, stage, new ArrayList<>(vars));
	}
	@Override
	public List<VariableExpr> getVars() {
		List<VariableExpr> list = new ArrayList<>();
		exprs.forEach(e -> list.addAll(e.getVars()));
		return list;
	}
	@Override
	public <T> T accept(IVisitor<T> v) {
		return v.visit(this);
	}


	@Override
	public String toString() {
		StringJoiner joiner = new StringJoiner(", ");
		exprs.forEach(e -> joiner.add(e.toString()));
		return name + "(" + joiner + ")";
	}
}
