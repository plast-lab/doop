package deepdoop.datalog;

public class AggregationElement implements IElement {

	String _variable;
	PredicateInstance _predicate;
	IElement _body;

	public AggregationElement(String variable, PredicateInstance predicate, IElement body) {
		_variable = variable;
		_predicate = predicate;
		_body = body;
	}
	public AggregationElement(String variable, PredicateInstance predicate) {
		this(variable, predicate, null);
	}

	@Override
	public void normalize() {}

	@Override
	public String toString() {
		return "agg<<" + _variable + " = " + _predicate + ">> " + _body;
	}
}
