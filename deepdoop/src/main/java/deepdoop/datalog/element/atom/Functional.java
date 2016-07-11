package deepdoop.datalog.element.atom;

import deepdoop.actions.IVisitable;
import deepdoop.actions.IVisitor;
import deepdoop.datalog.expr.IExpr;
import deepdoop.datalog.expr.VariableExpr;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

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
	public IVisitable accept(IVisitor v) {
		v.enter(this);
		Map<IVisitable, IVisitable> m = new HashMap<>();
		for (IExpr e : keyExprs) m.put(e, e.accept(v));
		m.put(valueExpr, valueExpr.accept(v));
		return v.exit(this, m);
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
		VariableExpr valueVar = (VariableExpr) vars.remove(vars.size()-1);
		return new Functional(name, stage, new ArrayList<>(vars), valueVar);
	}

	@Override
	public String toString() {
		StringJoiner joiner = new StringJoiner(", ");
		for (IExpr e : keyExprs) joiner.add(e.toString());
		return name + (stage == null ? "" : stage) + "[" + joiner + "]" + (valueExpr != null ? " = " + valueExpr : "");
	}
}
