package deepdoop.datalog.expr;

import deepdoop.actions.IVisitable;
import deepdoop.actions.IVisitor;
import java.util.Collections;
import java.util.Map;

public class GroupExpr implements IExpr {

	public final IExpr expr;

	public GroupExpr(IExpr expr) {
		this.expr  = expr;
	}

	@Override
	public IVisitable accept(IVisitor v) {
		v.enter(this);
		Map<IVisitable, IVisitable> m =
			Collections.singletonMap(expr, expr.accept(v));
		return v.exit(this, m);
	}

	@Override
	public String toString() {
		return "(" + expr + ")";
	}
}
