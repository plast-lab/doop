package deepdoop.datalog;

import java.util.Map;

public class Rule implements IInitializable<Rule> {

	IElement _head;
	IElement _body;

	public Rule(IElement head, IElement body) {
		_head = head;
		_body = body;
	}

	@Override
	public Rule init(String id) {
		return new Rule(_head.init(id), (_body != null ? _body.init(id) : null));
	}

	public Map<String, IAtom> getAtoms() {
		return _head.getAtoms();
	}

	public boolean isDirective() {
		Map<String, IAtom> atoms = _head.getAtoms();
		return _body == null && atoms.size() == 1 && atoms.values().iterator().next() instanceof Directive;
	}

	@Override
	public String toString() {
		return _head + (_body != null ? " <- " + _body : "") + ".";
	}
}
