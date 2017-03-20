package org.clyze.deepdoop.actions

import org.clyze.deepdoop.datalog.*
import org.clyze.deepdoop.datalog.clause.*
import org.clyze.deepdoop.datalog.component.*
import org.clyze.deepdoop.datalog.element.*
import org.clyze.deepdoop.datalog.element.atom.*
import org.clyze.deepdoop.datalog.expr.*

class PostOrderVisitor<T> implements IVisitor<T> {

	protected IActor<T> _actor

	PostOrderVisitor(IActor<T> actor) {
		_actor = actor
	}


	@Override
	T visit(Program n) {
		_actor.enter(n)
		Map<IVisitable, T> m = [:]
		m[n.globalComp] = n.globalComp.accept(this)
		n.comps.values().each{ m[it] = it.accept(this) }
		return _actor.exit(n, m)
	}

	@Override
	T visit(CmdComponent n) {
		_actor.enter(n)
		Map<IVisitable, T> m = [:]
		n.exports.each{      m[it] = it.accept(this) }
		n.imports.each{      m[it] = it.accept(this) }
		n.declarations.each{ m[it] = it.accept(this) }
		n.rules.each{        m[it] = it.accept(this) }
		return _actor.exit(n, m)
	}
	@Override
	T visit(Component n) {
		_actor.enter(n)
		Map<IVisitable, T> m = [:]
		n.declarations.each{ m[it] = it.accept(this) }
		n.constraints.each{  m[it] = it.accept(this) }
		n.rules.each{        m[it] = it.accept(this) }
		return _actor.exit(n, m)
	}

	@Override
	T visit(Constraint n) {
		_actor.enter(n)
		Map<IVisitable, T> m = [:]
		m[n.head] = n.head.accept(this)
		m[n.body] = n.body.accept(this)
		return _actor.exit(n, m)
	}
	@Override
	T visit(Declaration n) {
		_actor.enter(n)
		Map<IVisitable, T> m = [:]
		m[n.atom] = n.atom.accept(this)
		n.types.each{ m[it] = it.accept(this) }
		return _actor.exit(n, m)
	}
	@Override
	T visit(RefModeDeclaration n) {
		_actor.enter(n)
		Map<IVisitable, T> m = [:]
		m[n.atom] = n.atom.accept(this)
		n.types.each{ m[t] = it.accept(this) }
		return _actor.exit(n, m)
	}
	@Override
	T visit(Rule n) {
		_actor.enter(n)
		Map<IVisitable, T> m = [:]
		m[n.head] = n.head.accept(this)
		if (n.body != null) m[n.body] = n.body.accept(this)
		return _actor.exit(n, m)
	}

	@Override
	T visit(AggregationElement n) {
		_actor.enter(n)
		Map<IVisitable, T> m = [:]
		m[n.var] = n.var.accept(this)
		m[n.predicate] = n.predicate.accept(this)
		m[n.body] = n.body.accept(this)
		return _actor.exit(n, m)
	}
	@Override
	T visit(ComparisonElement n) {
		_actor.enter(n)
		Map<IVisitable, T> m = [:]
		m[n.expr] = n.expr.accept(this)
		return _actor.exit(n, m)
	}
	@Override
	T visit(GroupElement n) {
		_actor.enter(n)
		Map<IVisitable, T> m = [:]
		m[n.element] = n.element.accept(this)
		return _actor.exit(n, m)
	}
	@Override
	T visit(LogicalElement n) {
		_actor.enter(n)
		Map<IVisitable, T> m = [:]
		n.elements.each{ m[it] = it.accept(this) }
		return _actor.exit(n, m)
	}
	@Override
	T visit(NegationElement n) {
		_actor.enter(n)
		Map<IVisitable, T> m = [:]
		m[n.element] = n.element.accept(this)
		return _actor.exit(n, m)
	}

	@Override
	T visit(Directive n) {
		_actor.enter(n)
		Map<IVisitable, T> m = [:]
		if (n.backtick != null) m[n.backtick] = n.backtick.accept(this)
		if (n.constant != null) m[n.constant] = n.constant.accept(this)
		return _actor.exit(n, m)
	}
	@Override
	T visit(Functional n) {
		_actor.enter(n)
		Map<IVisitable, T> m = [:]
		n.keyExprs.each{ m[it] = it.accept(this) }
		if (n.valueExpr != null) m[n.valueExpr] = n.valueExpr.accept(this)
		return _actor.exit(n, m)
	}
	@Override
	T visit(Predicate n) {
		_actor.enter(n)
		Map<IVisitable, T> m = [:]
		n.exprs.each{ m[it] = it.accept(this) }
		return _actor.exit(n, m)
	}
	@Override
	T visit(Entity n) {
		_actor.enter(n)
		Map<IVisitable, T> m = [:]
		n.exprs.each{ m[it] = it.accept(this) }
		return _actor.exit(n, m)
	}
	@Override
	T visit(Primitive n) {
		_actor.enter(n)
		Map<IVisitable, T> m = [:]
		m[n.var] = n.var.accept(this)
		return _actor.exit(n, m)
	}
	@Override
	T visit(RefMode n) {
		_actor.enter(n)
		Map<IVisitable, T> m = [:]
		m[n.entityVar] = n.entityVar.accept(this)
		m[n.valueExpr] = n.valueExpr.accept(this)
		return _actor.exit(n, m)
	}
	@Override
	T visit(StubAtom n) {
		_actor.enter(n)
		return _actor.exit(n, [:])
	}

	@Override
	T visit(BinaryExpr n) {
		_actor.enter(n)
		Map<IVisitable, T> m = [:]
		m[n.left] = n.left.accept(this)
		m[n.right] = n.right.accept(this)
		return _actor.exit(n, m)
	}
	@Override
	T visit(ConstantExpr n) {
		_actor.enter(n)
		return _actor.exit(n, [:])
	}
	@Override
	T visit(FunctionalHeadExpr n) {
		_actor.enter(n)
		Map<IVisitable, T> m = [:]
		m[n.functional] = n.functional.accept(this)
		return _actor.exit(n, m)
	}
	@Override
	T visit(GroupExpr n) {
		_actor.enter(n)
		Map<IVisitable, T> m = [:]
		m[n.expr] = n.expr.accept(this)
		return _actor.exit(n, m)
	}
	@Override
	T visit(VariableExpr n) {
		_actor.enter(n)
		return _actor.exit(n, [:])
	}
}
