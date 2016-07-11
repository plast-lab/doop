package deepdoop.datalog.element;

import deepdoop.actions.IVisitable;
import deepdoop.actions.IVisitor;
import deepdoop.datalog.BinOperator;
import deepdoop.datalog.expr.BinaryExpr;
import deepdoop.datalog.expr.IExpr;
import java.util.Collections;
import java.util.Map;

public class ComparisonElement implements IElement {

	public final BinaryExpr expr;

	public ComparisonElement(IExpr left, BinOperator op, IExpr right) {
		this.expr = new BinaryExpr(left, op, right);
	}
	public ComparisonElement(BinaryExpr expr) {
		this.expr = expr;
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
		return expr.toString();
	}
}
