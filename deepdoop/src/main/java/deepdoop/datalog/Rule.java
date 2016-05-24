package deepdoop.datalog;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Rule implements IInitializable<Rule> {

	LogicalElement _head;
	IElement       _body;
	public final boolean isDirective;

	public Rule(LogicalElement head, IElement body) {
		_head = head;
		_body = body;

		List<? extends IElement> elements = _head.getElements();
		isDirective = (_body == null && elements.size() == 1 && elements.get(0) instanceof Directive);
	}

	@Override
	public Rule init(Initializer ini) {
		return new Rule(_head.init(ini), (_body != null ? _body.init(ini) : null));
	}

	public Map<String, IAtom> getAtoms() {
		Map<String, IAtom> map = new HashMap<>();
		for (IElement e : _head.getElements()) {
			IAtom a = (IAtom) e;
			map.put(a.name(), a);
		}
		return map;
	}

	@Override
	public String toString() {
		return _head + (_body != null ? " <- " + _body : "") + ".";
	}
}
