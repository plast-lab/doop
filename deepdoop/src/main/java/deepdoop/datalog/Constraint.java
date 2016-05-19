package deepdoop.datalog;

import java.util.Map;

public class Constraint implements IInitializable<Constraint> {

	IElement _head;
	IElement _body;

	public Constraint(IElement head, IElement body) {
		_head = head;
		_body = body;
	}

	@Override
	public Constraint init(String id) {
		return new Constraint(_head.init(id), _body.init(id));
	}

	@Override
	public String toString() {
		return _head + (_body != null ? " -> " + _body : "") + ".";
	}
}
