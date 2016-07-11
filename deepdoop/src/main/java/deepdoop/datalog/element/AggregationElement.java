package deepdoop.datalog.element;

import deepdoop.actions.IVisitable;
import deepdoop.actions.IVisitor;
import deepdoop.datalog.element.atom.Predicate;
import deepdoop.datalog.expr.VariableExpr;
import java.util.HashMap;
import java.util.Map;

public class AggregationElement implements IElement {

	public final VariableExpr var;
	public final Predicate    predicate;
	public final IElement     body;

	public AggregationElement(VariableExpr var, Predicate predicate, IElement body) {
		this.var       = var;
		this.predicate = predicate;
		this.body      = body;
	}

	@Override
	public IVisitable accept(IVisitor v) {
		v.enter(this);
		Map<IVisitable, IVisitable> m = new HashMap<>();
		m.put(var, var.accept(v));
		m.put(predicate, predicate.accept(v));
		m.put(body, body.accept(v));
		return v.exit(this, m);
	}

	@Override
	public String toString() {
		return "agg<<" + var + " = " + predicate + ">> " + body;
	}
}
