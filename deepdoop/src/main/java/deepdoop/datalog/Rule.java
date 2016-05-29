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

	public Map<String, IAtom> getDeclaringAtoms() {
		Map<String, IAtom> map = new HashMap<>();
		for (IElement e : _head.getElements())
			map.put(((IAtom)e).name(), (IAtom)e);
		return map;
	}

	@Override
	public Rule init(Initializer ini) {
		return new Rule(_head.init(ini), (_body != null ? _body.init(ini) : null));
	}

	@Override
	public String toString() {
		return _head + (_body != null ? " <- " + _body : "") + ".";
	}
}
