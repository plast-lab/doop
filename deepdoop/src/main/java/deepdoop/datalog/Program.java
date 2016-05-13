package deepdoop.datalog;

import java.util.HashMap;
import java.util.Map;
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
		return null;
	}

	@Override
	public String toString() {
		return null;
		//StringBuilder builder = new StringBuilder();
		//for (Predicate p : _predicates) builder.append(p + "\n");
		//for (Predicate p : _types) builder.append(p + "\n");
		//builder.append("\n");
		//for (Rule r : _rules) builder.append(r + "\n");
		//return builder.toString();
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
