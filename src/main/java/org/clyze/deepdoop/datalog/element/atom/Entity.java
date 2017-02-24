package org.clyze.deepdoop.datalog.element.atom;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import org.clyze.deepdoop.actions.IVisitor;
import org.clyze.deepdoop.datalog.expr.*;

public class Entity extends Predicate {

	public Entity(String name, String stage, List<IExpr> exprs) {
		super(name, stage, exprs);
	}
	public Entity(String name, List<IExpr> exprs) {
		this(name, null, exprs);
	}

	@Override
	public IAtom instantiate(String stage, List<VariableExpr> vars) {
		assert arity() == vars.size();
		assert arity() == 1;
		return new Entity(name, stage, new ArrayList<>(vars));
	}
	@Override
	public <T> T accept(IVisitor<T> v) {
		return v.visit(this);
	}
}

