package org.clyze.deepdoop.datalog.element.atom;

import java.util.ArrayList;
import java.util.List;
import org.clyze.deepdoop.actions.IVisitor;
import org.clyze.deepdoop.datalog.expr.*;

public class RefMode implements IAtom {

	public final String       name;
	public final String       stage;
	public final VariableExpr entityVar;
	public final IExpr        valueExpr;

	public RefMode(String name, String stage, VariableExpr entityVar, IExpr valueExpr) {
		assert !"@past".equals(stage);
		this.name      = name;
		this.stage     = stage;
		this.entityVar = entityVar;
		this.valueExpr = valueExpr;
	}

	@Override
	public String name() { return name; }
	@Override
	public String stage() { return stage; }
	@Override
	public int arity() { return 2; }
	@Override
	public IAtom instantiate(String stage, List<VariableExpr> vars) {
		assert arity() == vars.size();
		return new RefMode(name, stage, vars.get(0), vars.get(1));
	}
	@Override
	public List<VariableExpr> getVars() {
		List<VariableExpr> list = new ArrayList<>(valueExpr.getVars());
		list.add(0, entityVar);
		return list;
	}
	@Override
	public <T> T accept(IVisitor<T> v) {
		return v.visit(this);
	}


	@Override
	public String toString() {
		return name + "(" + entityVar + ":" + valueExpr + ")";
	}
}
