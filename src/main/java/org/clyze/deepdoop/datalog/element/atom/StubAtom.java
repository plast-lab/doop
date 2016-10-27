package org.clyze.deepdoop.datalog.element.atom;

import java.lang.UnsupportedOperationException;
import java.util.List;
import org.clyze.deepdoop.actions.IVisitor;
import org.clyze.deepdoop.datalog.expr.VariableExpr;

// Special class for when only a string is actually present but we need to
// treat it as an atom object
public class StubAtom implements IAtom {

	public final String name;

	public StubAtom(String name) {
		this.name = name;
	}

	@Override
	public String name() { return name; }
	@Override
	public String stage() { return null; }
	@Override
	public int arity() { throw new UnsupportedOperationException(); }
	@Override
	public List<VariableExpr> getVars() { throw new UnsupportedOperationException(); }
	@Override
	public IAtom instantiate(String stage, List<VariableExpr> vars) { throw new UnsupportedOperationException(); }


	@Override
	public <T> T accept(IVisitor<T> v) {
		return v.visit(this);
	}
}
