package org.clyze.deepdoop.datalog.element.atom

import org.clyze.deepdoop.actions.IVisitor
import org.clyze.deepdoop.datalog.expr.*
import org.clyze.deepdoop.system.*

class Predicate implements IAtom {

	public final String      name
	public final String      stage
	public final List<IExpr> exprs

	Predicate(String name, String stage, List<IExpr> exprs) {
		this.name  = name
		this.stage = stage
		this.exprs = exprs
	}
	Predicate(String name, List<IExpr> exprs) {
		this(name, null, exprs)
	}

	@Override
	String name() { return name }
	@Override
	String stage() { return stage }
	@Override
	int arity() { return exprs.size() }
	@Override
	IAtom instantiate(String stage, List<VariableExpr> vars) {
		assert arity() == vars.size()
		return new Predicate(name, stage, vars.collect())
	}
	@Override
	List<VariableExpr> getVars() {
		List<VariableExpr> list = new ArrayList<>();
		exprs.each{ e -> list.addAll(e.getVars()) }
		return list;
	}
	@Override
	<T> T accept(IVisitor<T> v) { return v.visit(this) }

	String toString() {
		def exprStr = exprs.collect{ it.toString() }.join(', ')
		return "$name($exprStr)"
	}

	SourceLocation location() { return null }
}
