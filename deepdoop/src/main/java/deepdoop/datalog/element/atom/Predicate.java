package deepdoop.datalog.element.atom;

import deepdoop.actions.IVisitor;
import deepdoop.datalog.expr.IExpr;
import deepdoop.datalog.expr.VariableExpr;
import java.util.ArrayList;
import java.util.List;

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
	public List<VariableExpr> getVars() {
		List<VariableExpr> list = new ArrayList<>();
		for (IExpr e : exprs) list.add((e instanceof VariableExpr ? (VariableExpr) e : null));
		return list;
	}
	@Override
	public IAtom instantiate(String stage, List<VariableExpr> vars) {
		assert arity() == vars.size();
		return new Predicate(name, stage, new ArrayList<>(vars));
	}


	@Override
	public <T> T accept(IVisitor<T> v) {
		return v.visit(this);
	}
}
