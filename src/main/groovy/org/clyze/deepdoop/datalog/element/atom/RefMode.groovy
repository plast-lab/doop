package org.clyze.deepdoop.datalog.element.atom

import org.clyze.deepdoop.actions.IVisitor
import org.clyze.deepdoop.datalog.expr.*
import org.clyze.deepdoop.system.*

class RefMode implements IAtom {

	public final String       name
	public final String       stage
	public final VariableExpr entityVar
	public final IExpr        valueExpr

	RefMode(String name, String stage, VariableExpr entityVar, IExpr valueExpr) {
		assert stage != "@past"
		this.name      = name
		this.stage     = stage
		this.entityVar = entityVar
		this.valueExpr = valueExpr
	}

	@Override
	String name() { return name }
	@Override
	String stage() { return stage }
	@Override
	int arity() { return 2 }
	@Override
	IAtom instantiate(String stage, List<VariableExpr> vars) {
		assert arity() == vars.size()
		return new RefMode(name, stage, vars.get(0), vars.get(1))
	}
	@Override
	List<VariableExpr> getVars() {
		return [entityVar] + valueExpr.getVars()
	}
	@Override
	<T> T accept(IVisitor<T> v) { return v.visit(this) }

	String toString() { return "$name($entityVar:$valueExpr)" }

	SourceLocation location() { return null }
}
