package deepdoop.datalog;

public class AggregationElement implements IElement {

	VariableExpr _variable;
	PredicateElement _predicate;
	IElement _body;

	public AggregationElement(VariableExpr variable, PredicateElement predicate, IElement body) {
		_variable = variable;
		_predicate = predicate;
		_body = body;
	}

	@Override
	public void normalize() {}

	@Override
	public String toString() {
		return "agg<<" + _variable + " = " + _predicate + ">> " + _body;
	}
}
