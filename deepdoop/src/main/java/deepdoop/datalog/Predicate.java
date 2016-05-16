package deepdoop.datalog;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

public class Predicate implements IAtom {

	String      _name;
	List<IAtom> _types;

	public Predicate(String name, List<IAtom> types) {
		_name  = name;
		_types = types;
	}
	public Predicate(String name) {
		this(name, null);
	}

	public void setTypes(List<IAtom> types) {
		_types = types;
	}

	@Override
	public Predicate init(String id) {
		List<IAtom> newTypes = new ArrayList<>();
		for (IAtom t : _types) newTypes.add(t.init(id));
		return new Predicate(id + ":" + _name, newTypes);
	}

	@Override
	public String name() {
		return _name;
	}

	@Override
	public int arity() {
		return _types.size();
	}

	@Override
	public String toString() {
		StringJoiner joiner = new StringJoiner(" x ");
		for (IAtom t : _types) joiner.add(t.name());
		return _name + "/" + arity() + " (" + joiner + ")";
	}
}
