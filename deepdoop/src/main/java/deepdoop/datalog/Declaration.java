package deepdoop.datalog;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

public class Declaration implements IInitializable<Declaration>, IAtomContainer {

	protected IAtom       _atom;
	protected List<IAtom> _types;

	public Declaration(IAtom atom, List<IAtom> types) {
		List<VariableExpr> varsInHead = atom.getExprsAsVars();

		int count = 0;
		IAtom[] ordered = new IAtom[types.size()];
		for (IAtom t : types) {
			List<VariableExpr> vars = t.getExprsAsVars();
			assert vars.size() == 1;
			count++;
			ordered[varsInHead.indexOf(vars.get(0))] = t;
		}
		assert (count == 0 || varsInHead.size() == count);
		types = new ArrayList<>(Arrays.asList(ordered));

		_atom  = atom;
		_types = types;
	}

	@Override
	public Map<String, IAtom> getAtoms() {
		return Collections.singletonMap(_atom.name(), _atom);
	}

	@Override
	public Map<String, IAtom> getDeclaringAtoms() {
		return getAtoms();
	}

	@Override
	public Declaration init(Initializer ini) {
		List<IAtom> newTypes = new ArrayList<>();
		for (IAtom t : _types) newTypes.add(t.init(ini));
		return new Declaration(_atom.init(ini), newTypes);
	}

	@Override
	public String toString() {
		StringJoiner joiner = new StringJoiner(", ");
		for (IAtom t : _types) joiner.add(t.toString());
		return _atom + " -> " + joiner + ".";
	}
}
