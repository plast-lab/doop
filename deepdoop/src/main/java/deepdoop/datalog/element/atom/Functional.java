package deepdoop.datalog.element.atom;

import deepdoop.actions.IVisitor;
import deepdoop.datalog.expr.IExpr;
import deepdoop.datalog.expr.VariableExpr;
import java.util.ArrayList;
import java.util.List;

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
	public List<VariableExpr> getVars() {
		List<VariableExpr> list = new ArrayList<>();
		for (IExpr e : keyExprs) list.add((e instanceof VariableExpr ? (VariableExpr) e : null));
		list.add((valueExpr instanceof VariableExpr ? (VariableExpr) valueExpr : null));
		return list;
	}
	@Override
	public IAtom instantiate(String stage, List<VariableExpr> vars) {
		assert arity() == vars.size();
		VariableExpr valueVar = (VariableExpr) vars.remove(vars.size()-1);
		return new Functional(name, stage, new ArrayList<>(vars), valueVar);
	}


	@Override
	public <T> T accept(IVisitor<T> v) {
		return v.visit(this);
	}
}
