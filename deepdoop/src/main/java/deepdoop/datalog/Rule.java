package deepdoop.datalog;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Rule implements IInitializable<Rule>, IAtomContainer {

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
	public Map<String, IAtom> getAtoms() {
		Map<String, IAtom> map = new HashMap<>();
		map.putAll(_head.getAtoms());
		if (_body != null)
			map.putAll(_body.getAtoms());
		return map;
	}

	@Override
	public Map<String, IAtom> getDeclaringAtoms() {
		Map<String, IAtom> map = new HashMap<>();
		map.putAll(_head.getAtoms());
		return map;
	}

	@Override
	public Map<String, IAtom> getInputAtoms() {
		Map<String, IAtom> atoms = getAtoms();
		Map<String, IAtom> declaringAtoms = getDeclaringAtoms();
		atoms.keySet().removeAll(declaringAtoms.keySet());
		return atoms;
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
