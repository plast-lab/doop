package deepdoop.datalog;

public class Rule {

	IElement _head;
	IElement _body;

	public Rule(IElement head, IElement body) {
		_head = head;
		_body = body;
	}

	@Override
	public String toString() {
		return _head + (_body != null ? " <- " + _body : "") + ".";
	}
}
