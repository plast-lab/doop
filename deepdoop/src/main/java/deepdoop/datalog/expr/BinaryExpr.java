package deepdoop.datalog.expr;

import deepdoop.actions.IVisitable;
import deepdoop.actions.IVisitor;
import deepdoop.datalog.BinOperator;
import java.util.HashMap;
import java.util.Map;

public class BinaryExpr implements IExpr {

	public final IExpr       left;
	public final BinOperator op;
	public final IExpr       right;

	public BinaryExpr(IExpr left, BinOperator op, IExpr right) {
		this.left  = left;
		this.op    = op;
		this.right = right;
	}

	@Override
	public IVisitable accept(IVisitor v) {
		v.enter(this);
		Map<IVisitable, IVisitable> m = new HashMap<>();
		m.put(left, left.accept(v));
		m.put(right, right.accept(v));
		return v.exit(this, m);
	}

	@Override
	public String toString() {
		return left + " " + op + " " + right;
	}
}
