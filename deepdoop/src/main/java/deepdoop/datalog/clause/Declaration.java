package deepdoop.datalog.clause;

import deepdoop.actions.IVisitable;
import deepdoop.actions.IVisitor;
import deepdoop.datalog.element.atom.IAtom;
import deepdoop.datalog.expr.VariableExpr;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

public class Declaration implements IVisitable {

	public final IAtom       atom;
	public final List<IAtom> types;

	public Declaration(IAtom atom, Set<IAtom> types) {
		List<VariableExpr> varsInHead = atom.getVars();

		int count = 0;
		IAtom[] ordered = new IAtom[types.size()];
		for (IAtom t : types) {
			List<VariableExpr> vars = t.getVars();
			assert vars.size() == 1;
			count++;
			ordered[varsInHead.indexOf(vars.get(0))] = t;
		}
		assert (count == 0 || varsInHead.size() == count);

		this.atom  = atom;
		this.types = new ArrayList<>(Arrays.asList(ordered));
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
		StringJoiner joiner = new StringJoiner(", ");
		for (IAtom t : types) joiner.add(t.toString());
		return atom + " -> " + joiner + ".";
	}
}
