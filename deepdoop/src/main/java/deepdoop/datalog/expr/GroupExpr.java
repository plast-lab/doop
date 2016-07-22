package deepdoop.datalog.expr;

import deepdoop.actions.IVisitor;

public class GroupExpr implements IExpr {

	public final IExpr expr;

	public GroupExpr(IExpr expr) {
		this.expr  = expr;
	}


	@Override
	public <T> T accept(IVisitor<T> v) {
		return v.visit(this);
	}
}
