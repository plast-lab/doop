package org.clyze.deepdoop.datalog.expr;

import java.util.List;
import org.clyze.deepdoop.actions.IVisitor;

public class GroupExpr implements IExpr {

	public final IExpr expr;

	public GroupExpr(IExpr expr) {
		this.expr  = expr;
	}


	@Override
	public List<VariableExpr> getVars() {
		return expr.getVars();
	}
	@Override
	public <T> T accept(IVisitor<T> v) {
		return v.visit(this);
	}
}
