package deepdoop.datalog.expr;

import deepdoop.actions.IVisitor;
import deepdoop.datalog.element.atom.Functional;
import java.util.List;

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
