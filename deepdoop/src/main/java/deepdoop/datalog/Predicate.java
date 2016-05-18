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
		return new Predicate(Names.nameId(_name, id), newTypes);
	}

	@Override
	public String name() {
		return _name;
	}

	@Override
	public IAtom.Type type() {
		return IAtom.Type.PREDICATE;
	}

	@Override
	public int arity() {
		return _types.size();
	}

	@Override
	public String toString() {
		//StringJoiner joiner = new StringJoiner(" x ");
		//for (IAtom t : _types) joiner.add(t.name());
		//return _name + "/" + arity() + " (" + joiner + ")";
		int arity = arity();
		List<IExpr> vars = Names.newVars(arity);
		StringJoiner joiner1 = new StringJoiner(", ");
		StringJoiner joiner2 = new StringJoiner(", ");
		for (int i = 0 ; i < arity ; i++) {
			IExpr v = vars.get(i);
			IAtom t = _types.get(i);
			joiner1.add(v.toString());
			joiner2.add(t.name() + "(" + v + ")");
		}
		return _name + "(" + joiner1 + ") -> " + joiner2 + ".";
	}
}
