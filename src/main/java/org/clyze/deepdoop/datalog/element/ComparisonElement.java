package org.clyze.deepdoop.datalog.element;

import java.util.List;
import org.clyze.deepdoop.actions.IVisitor;
import org.clyze.deepdoop.datalog.BinOperator;
import org.clyze.deepdoop.datalog.expr.*;
import org.clyze.deepdoop.system.*;

public class ComparisonElement implements IElement {

	public final BinaryExpr expr;

	public ComparisonElement(IExpr left, BinOperator op, IExpr right) {
		this.expr = new BinaryExpr(left, op, right);
		this._loc = SourceManager.v().getLastLoc();
	}
	public ComparisonElement(BinaryExpr expr) {
		this.expr = expr;
		this._loc = SourceManager.v().getLastLoc();
	}


	@Override
	public List<VariableExpr> getVars() {
		return expr.getVars();
	}
	@Override
	public <T> T accept(IVisitor<T> v) {
		return v.visit(this);
	}

	SourceLocation _loc;
	@Override
	public SourceLocation location() { return _loc; }


	@Override
	public String toString() {
		return expr.toString();
	}
}
