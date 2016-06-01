package deepdoop.datalog;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class RefModeDeclaration extends Declaration {

	public RefModeDeclaration(RefMode refmode, Predicate entity, Primitive primitive) {
		super(refmode, Arrays.asList(entity, primitive));
	}

	@Override
	public Map<String, IAtom> getAtoms() {
		Map<String, IAtom> map = new HashMap<>();
		map.put(_atom.name(), _atom);
		map.put(_types.get(0).name(), _types.get(0));
		return map;
	}

	@Override
	public RefModeDeclaration init(Initializer ini) {
		return new RefModeDeclaration((RefMode)_atom.init(ini), (Predicate)_types.get(0).init(ini), (Primitive)_types.get(1).init(ini));
	}

	@Override
	public String toString() {
		return _types.get(0) + ", " + _atom + " -> " + _types.get(1) + ".";
	}
}
