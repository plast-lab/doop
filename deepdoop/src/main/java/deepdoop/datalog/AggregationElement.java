package deepdoop.datalog;

public class AggregationElement implements IElement {

	VariableExpr     _variable;
	PredicateElement _predicate;
	IElement         _body;

	public AggregationElement(VariableExpr variable, PredicateElement predicate, IElement body) {
		_variable  = variable;
		_predicate = predicate;
		_body      = body;
	}

	@Override
	public AggregationElement init(String id) {
		return new AggregationElement(_variable, _predicate.init(id), _body.init(id));
	}

	@Override
	public void flatten() {
		_body.flatten();
	}

	@Override
	public String toString() {
		return "agg<<" + _variable + " = " + _predicate + ">> " + _body;
	}
}
