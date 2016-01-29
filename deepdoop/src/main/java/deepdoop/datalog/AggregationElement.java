package deepdoop.datalog;

public class AggregationElement implements IElement {

	String _variable;
	PredicateElement _predicate;
	IElement _body;

	public AggregationElement(String variable, PredicateElement predicate, IElement body) {
		_variable = variable;
		_predicate = predicate;
		_body = body;
	}
	public AggregationElement(String variable, PredicateElement predicate) {
		this(variable, predicate, null);
	}

	@Override
	public void normalize() {}

	@Override
	public String toString() {
		return "agg<<" + _variable + " = " + _predicate + ">> " + _body;
	}
}
