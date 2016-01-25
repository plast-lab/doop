package deepdoop.datalog;

import java.util.List;
import java.util.StringJoiner;

public class PredicateInstance extends Predicate {
	List<Object> _parameters;
	boolean _onlyHead;

	public PredicateInstance(String name, List<Object> parameters) {
		this(name, parameters, false);
	}
	public PredicateInstance(String name, List<Object> parameters, boolean isFunctional) {
		super(name, null, isFunctional);
		_parameters = parameters;

		//int lastIndex = parameters.size()-1;
		//Object lastParam = parameters.get(lastIndex);
		//if (lastParam instanceof Variable && ((Variable) lastParam).isEmpty) {
		//	parameters.remove(lastIndex);
		//	_onlyHead = true;
		//} else
		//	_onlyHead = false;
	}

	@Override
	public String toString() {
		StringJoiner joiner = new StringJoiner(", ");
		for (Object p : _parameters) joiner.add(p.toString());
		String t = joiner.toString();
		if (isFunctional) {
			int pos = t.lastIndexOf(", ");
			if (!_onlyHead)
				t = new StringBuilder(t).replace(pos, pos+2, "] = ").toString();
			else
				t += "]";
			return _name + "[" + t;
		} else
			return _name + "(" + t + ")";
	}
}
