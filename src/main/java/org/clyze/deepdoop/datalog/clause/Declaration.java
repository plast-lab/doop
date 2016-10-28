package org.clyze.deepdoop.datalog.clause;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.clyze.deepdoop.actions.IVisitable;
import org.clyze.deepdoop.actions.IVisitor;
import org.clyze.deepdoop.datalog.DeepDoopException;
import org.clyze.deepdoop.datalog.DeepDoopException.Error;
import org.clyze.deepdoop.datalog.element.atom.IAtom;
import org.clyze.deepdoop.datalog.expr.VariableExpr;

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
			int index = varsInHead.indexOf(vars.get(0));
			if (index == -1)
				throw new DeepDoopException(Error.DECL_UNKNOWN_VAR, vars.get(0).name);
			ordered[index] = t;
		}
		assert (count == 0 || varsInHead.size() == count);

		this.atom  = atom;
		this.types = new ArrayList<>(Arrays.asList(ordered));
	}


	@Override
	public <T> T accept(IVisitor<T> v) {
		return v.visit(this);
	}
}
