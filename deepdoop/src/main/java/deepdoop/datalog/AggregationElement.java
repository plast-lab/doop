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
	public void flatten() {}

	@Override
	public IElement init(String id) {
		return new AggregationElement(_variable, (PredicateElement)_predicate.init(id), _body.init(id));
	}

	@Override
	public String toString() {
		return "agg<<" + _variable + " = " + _predicate + ">> " + _body;
	}
}
