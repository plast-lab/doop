package deepdoop.datalog.clause;

import deepdoop.actions.IVisitable;
import deepdoop.actions.IVisitor;
import deepdoop.datalog.element.atom.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class RefModeDeclaration extends Declaration {

	public RefModeDeclaration(RefMode refmode, Predicate entity, Primitive primitive) {
		super(refmode, new HashSet<>(Arrays.asList(entity, primitive)));
	}

	@Override
	public IVisitable accept(IVisitor v) {
		v.enter(this);
		Map<IVisitable, IVisitable> m = new HashMap<>();
		m.put(atom, atom.accept(v));
		for (IAtom t : types) m.put(t, t.accept(v));
		return v.exit(this, m);
	}

	@Override
	public String toString() {
		return types.get(0) + ", " + atom + " -> " + types.get(1) + ".";
	}
}
