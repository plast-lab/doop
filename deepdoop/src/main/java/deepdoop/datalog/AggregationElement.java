package deepdoop.datalog;

public class AggregationElement implements IElement {

	VariableExpr _variable;
	Predicate    _predicate;
	IElement     _body;

	public AggregationElement(VariableExpr variable, Predicate predicate, IElement body) {
		_variable  = variable;
		_predicate = predicate;
		_body      = body;
	}

	@Override
	public AggregationElement init(Initializer ini) {
		return new AggregationElement(_variable, _predicate.init(ini), _body.init(ini));
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
