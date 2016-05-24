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
	public Constraint init(Initializer ini) {
		return new Constraint(_head.init(ini), _body.init(ini));
	}

	@Override
	public String toString() {
		return _head + (_body != null ? " -> " + _body : "") + ".";
	}
}
