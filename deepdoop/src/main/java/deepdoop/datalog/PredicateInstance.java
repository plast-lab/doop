package deepdoop.datalog;

import java.util.List;
import java.util.StringJoiner;

public class PredicateInstance extends Predicate implements IElement {
	List<Object> _parameters;
	Object _lastParameter;

	public PredicateInstance(String name, List<Object> parameters) {
		this(name, parameters, false);
	}
	public PredicateInstance(String name, List<Object> parameters, boolean isFunctional) {
		super(name, null, isFunctional);
		_parameters = parameters;
		_lastParameter = null;

		if (isFunctional) {
			int lastIndex = parameters.size()-1;
			_lastParameter = parameters.get(lastIndex);
			parameters.remove(lastIndex);
			//if (_lastParam instanceof Variable && ((Variable) lastParam).isEmpty) {
		}
	}

	@Override
	public String toString() {
		StringJoiner joiner = new StringJoiner(", ");
		for (Object p : _parameters) joiner.add(p.toString());
		String t = joiner.toString();
		if (isFunctional) {
			if (_lastParameter instanceof Variable && ((Variable) _lastParameter).isEmpty)
				return _name + "[" + t + "]";
			else
				return _name + "[" + t + "] = " + _lastParameter;
		} else
			return _name + "(" + t + ")";
	}
}
