package deepdoop.datalog;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

public class Functional implements IAtom {

	String      _name;
	List<IAtom> _keyTypes;
	IAtom       _valueType;

	public Functional(String name, List<IAtom> keyTypes, IAtom valueType) {
		_name      = name;
		_keyTypes  = keyTypes;
		_valueType = valueType;
	}
	public Functional(String name) {
		this(name, null, null);
	}

	public void setTypes(List<IAtom> keyTypes, IAtom valueType) {
		_keyTypes  = keyTypes;
		_valueType = valueType;
	}

	@Override
	public Functional init(String id) {
		List<IAtom> newKeyTypes = new ArrayList<>();
		for (IAtom t : _keyTypes) newKeyTypes.add(t.init(id));
		return new Functional(id + ":" + _name, newKeyTypes, _valueType.init(id));
	}

	@Override
	public String name() {
		return _name;
	}

	@Override
	public int arity() {
		return _keyTypes.size() + 1;
	}

	@Override
	public String toString() {
		StringJoiner joiner = new StringJoiner(" x ");
		for (IAtom t : _keyTypes) joiner.add(t.name());
		return _name + "/" + arity() + " (" + joiner + " -> " + _valueType.name() + ")";
	}
}
