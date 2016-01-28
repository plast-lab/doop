package deepdoop.datalog;

import java.util.List;
import java.util.StringJoiner;

public class FunctionalHeadInstance extends FunctionalInstance {

	public FunctionalHeadInstance(String name, List<Object> keyParameters) {
		super(name, keyParameters, null);
	}

	@Override
	public void normalize() {}

	@Override
	public String toString() {
		StringJoiner joiner = new StringJoiner(", ");
		for (Object p : _parameters) joiner.add(p.toString());
		return _name + "[" + joiner + "]";
	}
}
