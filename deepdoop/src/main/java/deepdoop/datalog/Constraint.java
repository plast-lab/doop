package deepdoop.datalog;

import java.util.HashMap;
import java.util.Map;

public class Constraint implements IInitializable<Constraint>, IAtomContainer {

	IElement _head;
	IElement _body;

	public Constraint(IElement head, IElement body) {
		_head = head;
		_body = body;
	}

	@Override
	public Map<String, IAtom> getAtoms() {
		Map<String, IAtom> map = new HashMap<>();
		map.putAll(_head.getAtoms());
		map.putAll(_body.getAtoms());
		return map;
	}

	@Override
	public Constraint init(Initializer ini) {
		return new Constraint(_head.init(ini), _body.init(ini));
	}

	@Override
	public String toString() {
		return _head + " -> " + _body + ".";
	}
}
