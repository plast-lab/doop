package org.clyze.deepdoop.datalog.expr;

import java.util.ArrayList;
import java.util.List;
import org.clyze.deepdoop.actions.*;
import org.clyze.deepdoop.datalog.*;

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
	public List<VariableExpr> getVars() {
		List<VariableExpr> list = new ArrayList<>();
		list.addAll(left.getVars());
		list.addAll(right.getVars());
		return list;
	}
	@Override
	public <T> T accept(IVisitor<T> v) {
		return v.visit(this);
	}


	@Override
	public String toString() {
		return left + " " + op + " " + right;
	}
}
