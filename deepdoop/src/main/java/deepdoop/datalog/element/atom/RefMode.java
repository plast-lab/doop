package deepdoop.datalog.element.atom;

import deepdoop.actions.IVisitable;
import deepdoop.actions.IVisitor;
import deepdoop.datalog.expr.IExpr;
import deepdoop.datalog.expr.VariableExpr;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
	public IVisitable accept(IVisitor v) {
		v.enter(this);
		Map<IVisitable, IVisitable> m = new HashMap<>();
		m.put(entityVar, entityVar.accept(v));
		m.put(valueExpr, valueExpr.accept(v));
		return v.exit(this, m);
	}

	@Override
	public String name() { return name; }
	@Override
	public String stage() { return stage; }
	@Override
	public int arity() { return 2; }
	@Override
	public List<VariableExpr> getVars() {
		return Arrays.asList(entityVar, (valueExpr instanceof VariableExpr ? (VariableExpr) valueExpr : null));
	}
	@Override
	public IAtom instantiate(String stage, List<VariableExpr> vars) {
		return new RefMode(name, stage, vars.get(0), vars.get(1));
	}

	@Override
	public String toString() {
		return name + (stage == null ? "" : stage) + "(" + entityVar + ":" + valueExpr + ")";
	}
}
