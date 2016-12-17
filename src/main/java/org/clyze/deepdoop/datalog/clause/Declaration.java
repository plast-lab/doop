package org.clyze.deepdoop.datalog.clause;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.clyze.deepdoop.actions.*;
import org.clyze.deepdoop.datalog.element.atom.IAtom;
import org.clyze.deepdoop.datalog.expr.*;
import org.clyze.deepdoop.system.*;

public class Declaration implements IVisitable, ISourceItem {

	public final IAtom       atom;
	public final List<IAtom> types;
	SourceLocation           _loc;

	public Declaration(IAtom atom, Set<IAtom> types) {
		this(atom, types, null);
	}
	public Declaration(IAtom atom, Set<IAtom> types, SourceLocation loc) {
		this.atom  = atom;
		this._loc  = loc;

		List<VariableExpr> varsInHead = atom.getVars();

		int typesCount = types.size();
		IAtom[] ordered = new IAtom[typesCount];
		for (IAtom t : types) {
			List<VariableExpr> vars = t.getVars();
			assert vars.size() == 1;
			int index = varsInHead.indexOf(vars.get(0));
			if (index == -1)
				ErrorManager.error(location(), ErrorId.UNKNOWN_VAR, vars.get(0).name);
			ordered[index] = t;
		}
		assert (typesCount == 0 || typesCount == varsInHead.size());

		this.types = new ArrayList<>(Arrays.asList(ordered));
	}


	@Override
	public <T> T accept(IVisitor<T> v) {
		return v.visit(this);
	}
	@Override
	public SourceLocation location() {
		return _loc;
	}
}
