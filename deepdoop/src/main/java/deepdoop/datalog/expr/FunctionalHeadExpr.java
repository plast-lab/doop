package deepdoop.datalog.expr;

import deepdoop.actions.IVisitable;
import deepdoop.actions.IVisitor;
import deepdoop.datalog.element.atom.Functional;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
	public IVisitable accept(IVisitor v) {
		v.enter(this);
		Map<IVisitable, IVisitable> m =
			Collections.singletonMap(functional, functional.accept(v));
		return v.exit(this, m);
	}

	@Override
	public String toString() {
		return functional.toString();
	}
}
