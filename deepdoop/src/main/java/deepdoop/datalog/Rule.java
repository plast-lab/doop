package deepdoop.datalog;

public class Rule {

	IElement _head;
	IElement _body;

	public Rule(IElement head, IElement body) {
		_head = head;
		_body = body;
	}

	public Rule init(String id) {
		return new Rule(_head.init(id), _body.init(id));
	}

	@Override
	public String toString() {
		return _head + (_body != null ? " <- " + _body : "") + ".";
	}
}
