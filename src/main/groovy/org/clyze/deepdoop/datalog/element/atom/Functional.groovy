package org.clyze.deepdoop.datalog.element.atom

import org.clyze.deepdoop.actions.IVisitor
import org.clyze.deepdoop.datalog.expr.*
import org.clyze.deepdoop.system.*

class Functional implements IAtom {

	public final String      name
	public final String      stage
	public final List<IExpr> keyExprs
	public final IExpr       valueExpr

	Functional(String name, String stage, List<IExpr> keyExprs, IExpr valueExpr) {
		this.name      = name
		this.stage     = stage
		this.keyExprs  = keyExprs
		this.valueExpr = valueExpr
	}

	@Override
	String name() { return name }
	@Override
	String stage() { return stage }
	@Override
	int arity() { return keyExprs.size() + 1 }
	@Override
	IAtom instantiate(String stage, List<VariableExpr> vars) {
		assert arity() == vars.size()
		def varsCopy = vars.collect()
		def valueVar = varsCopy.pop()
		return new Functional(name, stage, varsCopy, valueVar)
	}
	@Override
	List<VariableExpr> getVars() {
		def list = keyExprs.collect{ it.getVars() }.flatten()
		if (valueExpr != null) list.addAll(valueExpr.getVars())
		return list
	}
	@Override
	<T> T accept(IVisitor<T> v) { return v.visit(this) }

	String toString() {
		def exprStr = keyExprs.collect{ it.toString() }.join(', ')
		return "$name[$exprStr]" + (valueExpr == null ? '' : " = $valueExpr")
	}

	SourceLocation location() { return null }
}
