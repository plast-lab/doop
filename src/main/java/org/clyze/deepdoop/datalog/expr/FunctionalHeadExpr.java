package org.clyze.deepdoop.datalog.expr;

import java.util.List;
import org.clyze.deepdoop.actions.IVisitor;
import org.clyze.deepdoop.datalog.element.atom.Functional;

public class FunctionalHeadExpr implements IExpr {

	public final Functional functional;

	public FunctionalHeadExpr(String name, String stage, List<IExpr> keyExprs) {
		this.functional = new Functional(name, stage, keyExprs, null);
	}
	public FunctionalHeadExpr(Functional functional) {
		assert functional.valueExpr == null;
		this.functional = functional;
	}


	@Override
	public <T> T accept(IVisitor<T> v) {
		return v.visit(this);
	}
}
