package deepdoop.datalog;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class Program {

	Map<String, Component>   _comps;
	Map<String, String>      _inits;
	Map<String, Propagation> _propFrom;
	Map<String, Propagation> _propTo;

	public Program() {
		_comps     = new HashMap<>();
		_inits     = new HashMap<>();
		_propFrom  = new HashMap<>();
		_propTo    = new HashMap<>();
	}

	public void comp(Component comp) {
		_comps.put(comp.name, comp);
	}

	public void init(String id, String comp) {
		_inits.put(id, comp);
	}

	public void propagate(String fromId, Set<String> preds, String toId) {
		Propagation prop = new Propagation(fromId, preds, toId);
		_propFrom.put(fromId, prop);
		_propTo.put(toId, prop);
	}

	public Component flatten() {
		Component flat = new Component(Component.GLOBAL_COMP, null);
		Component global = _comps.get(Component.GLOBAL_COMP);
		flat.preds.addAll(global.preds);
		flat.types.addAll(global.types);
		flat.rules.addAll(global.rules);

		for (Entry<String, String> entry : _inits.entrySet()) {
			String id = entry.getKey();
			String comp = entry.getValue();
			Component c = _comps.get(comp).init(id, _comps);
			flat.preds.addAll(c.preds);
			flat.types.addAll(c.types);
			flat.rules.addAll(c.rules);
		}
		return flat;
	}

	@Override
	public String toString() {
		return flatten().toString();
	}
}


class Propagation {

	String      _fromId;
	Set<String> _preds;
	String      _toId;

	Propagation(String fromId, Set<String> preds, String toId) {
		_fromId = fromId;
		_preds  = preds;
		_toId   = toId;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Propagation))
			return false;
		Propagation p = (Propagation) o;
		return
			_fromId.equals(p._fromId) &&
			_preds.equals(p._preds) &&
			_toId.equals(p._toId);
	}
	@Override
	public int hashCode() {
		return (int) _fromId.hashCode() * _preds.hashCode() * _toId.hashCode();
	}
}
