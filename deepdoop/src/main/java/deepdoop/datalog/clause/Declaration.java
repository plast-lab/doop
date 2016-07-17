package deepdoop.datalog.clause;

import deepdoop.actions.IVisitable;
import deepdoop.actions.IVisitor;
import deepdoop.datalog.element.atom.IAtom;
import deepdoop.datalog.expr.VariableExpr;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
	public String toString() {
		StringJoiner joiner = new StringJoiner(", ");
		for (IAtom t : types) joiner.add(t.toString());
		return atom + " -> " + joiner + ".";
	}


	@Override
	public <T> T accept(IVisitor<T> v) {
		return v.visit(this);
	}
}
