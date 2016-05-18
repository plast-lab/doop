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
		return new Functional(Names.nameId(_name, id), newKeyTypes, _valueType.init(id));
	}

	@Override
	public String name() {
		return _name;
	}

	@Override
	public IAtom.Type type() {
		return IAtom.Type.FUNCTIONAL;
	}

	@Override
	public int arity() {
		return _keyTypes.size() + 1;
	}

	@Override
	public String toString() {
		//StringJoiner joiner = new StringJoiner(" x ");
		//for (IAtom t : _keyTypes) joiner.add(t.name());
		//return _name + "/" + arity() + " (" + joiner + " -> " + _valueType.name() + ")";
		int arity = arity();
		List<IExpr> vars = Names.newVars(arity);
		StringJoiner joiner1 = new StringJoiner(", ");
		StringJoiner joiner2 = new StringJoiner(", ");
		for (int i = 0 ; i < arity-1 ; i++) {
			IExpr v = vars.get(i);
			IAtom t = _keyTypes.get(i);
			joiner1.add(v.toString());
			joiner2.add(t.name() + "(" + v + ")");
		}
		IExpr valueVar = vars.get(vars.size()-1);
		joiner2.add(_valueType.name() + "(" + valueVar + ")");
		return _name + "[" + joiner1 + "] = " + valueVar + " -> " + joiner2 + ".";
	}
}
