package org.clyze.deepdoop.actions

import org.clyze.deepdoop.datalog.*
import org.clyze.deepdoop.datalog.clause.*
import org.clyze.deepdoop.datalog.component.*
import org.clyze.deepdoop.datalog.element.*
import org.clyze.deepdoop.datalog.element.atom.*
import org.clyze.deepdoop.datalog.expr.*

class PostOrderVisitor<T> implements IVisitor<T> {

	protected IActor<T> actor

	PostOrderVisitor(IActor<T> actor) {
		this.actor = actor
	}

	T visit(Program n) {
		actor.enter(n)
		Map<IVisitable, T> m = [:]
		m[n.globalComp] = n.globalComp.accept(this)
		n.comps.values().each{ m[it] = it.accept(this) }
		return actor.exit(n, m)
	}

	T visit(CmdComponent n) {
		actor.enter(n)
		Map<IVisitable, T> m = [:]
		n.exports.each{ m[it] = it.accept(this) }
		n.imports.each{ m[it] = it.accept(this) }
		n.declarations.each{ m[it] = it.accept(this) }
		n.rules.each{ m[it] = it.accept(this) }
		return actor.exit(n, m)
	}

	T visit(Component n) {
		actor.enter(n)
		Map<IVisitable, T> m = [:]
		n.declarations.each{ m[it] = it.accept(this) }
		n.constraints.each{ m[it] = it.accept(this) }
		n.rules.each{ m[it] = it.accept(this) }
		return actor.exit(n, m)
	}

	T visit(Constraint n) {
		actor.enter(n)
		Map<IVisitable, T> m = [:]
		m[n.head] = n.head.accept(this)
		m[n.body] = n.body.accept(this)
		return actor.exit(n, m)
	}

	T visit(Declaration n) {
		actor.enter(n)
		Map<IVisitable, T> m = [:]
		m[n.atom] = n.atom.accept(this)
		n.types.each{ m[it] = it.accept(this) }
		return actor.exit(n, m)
	}

	T visit(RefModeDeclaration n) {
		actor.enter(n)
		Map<IVisitable, T> m = [:]
		m[n.atom] = n.atom.accept(this)
		n.types.each{ m[it] = it.accept(this) }
		return actor.exit(n, m)
	}

	T visit(Rule n) {
		actor.enter(n)
		Map<IVisitable, T> m = [:]
		m[n.head] = n.head.accept(this)
		if (n.body) m[n.body] = n.body.accept(this)
		return actor.exit(n, m)
	}

	T visit(AggregationElement n) {
		actor.enter(n)
		Map<IVisitable, T> m = [:]
		m[n.var] = n.var.accept(this)
		m[n.predicate] = n.predicate.accept(this)
		m[n.body] = n.body.accept(this)
		return actor.exit(n, m)
	}

	T visit(ComparisonElement n) {
		actor.enter(n)
		Map<IVisitable, T> m = [:]
		m[n.expr] = n.expr.accept(this)
		return actor.exit(n, m)
	}

	T visit(GroupElement n) {
		actor.enter(n)
		Map<IVisitable, T> m = [:]
		m[n.element] = n.element.accept(this)
		return actor.exit(n, m)
	}

	T visit(LogicalElement n) {
		actor.enter(n)
		Map<IVisitable, T> m = [:]
		n.elements.each{ m[it] = it.accept(this) }
		return actor.exit(n, m)
	}

	T visit(NegationElement n) {
		actor.enter(n)
		Map<IVisitable, T> m = [:]
		m[n.element] = n.element.accept(this)
		return actor.exit(n, m)
	}

	T visit(Constructor n) {
		actor.enter(n)
		Map<IVisitable, T> m = [:]
		n.keyExprs.each{ m[it] = it.accept(this) }
		if (n.valueExpr) m[n.valueExpr] = n.valueExpr.accept(this)
		m[n.type] = n.type.accept(this)
		return actor.exit(n, m)
	}

	T visit(Directive n) {
		actor.enter(n)
		Map<IVisitable, T> m = [:]
		if (n.backtick) m[n.backtick] = n.backtick.accept(this)
		if (n.constant) m[n.constant] = n.constant.accept(this)
		return actor.exit(n, m)
	}

	T visit(Entity n) {
		actor.enter(n)
		Map<IVisitable, T> m = [:]
		n.exprs.each{ m[it] = it.accept(this) }
		return actor.exit(n, m)
	}

	T visit(Functional n) {
		actor.enter(n)
		Map<IVisitable, T> m = [:]
		n.keyExprs.each{ m[it] = it.accept(this) }
		if (n.valueExpr) m[n.valueExpr] = n.valueExpr.accept(this)
		return actor.exit(n, m)
	}

	T visit(Predicate n) {
		actor.enter(n)
		Map<IVisitable, T> m = [:]
		n.exprs.each{ m[it] = it.accept(this) }
		return actor.exit(n, m)
	}

	T visit(Primitive n) {
		actor.enter(n)
		Map<IVisitable, T> m = [:]
		m[n.var] = n.var.accept(this)
		return actor.exit(n, m)
	}

	T visit(RefMode n) {
		actor.enter(n)
		Map<IVisitable, T> m = [:]
		m[n.entityVar] = n.entityVar.accept(this)
		m[n.valueExpr] = n.valueExpr.accept(this)
		return actor.exit(n, m)
	}

	T visit(Stub n) {
		actor.enter(n)
		return actor.exit(n, [:])
	}

	T visit(BinaryExpr n) {
		actor.enter(n)
		Map<IVisitable, T> m = [:]
		m[n.left] = n.left.accept(this)
		m[n.right] = n.right.accept(this)
		return actor.exit(n, m)
	}

	T visit(ConstantExpr n) {
		actor.enter(n)
		return actor.exit(n, [:])
	}

	T visit(FunctionalHeadExpr n) {
		actor.enter(n)
		Map<IVisitable, T> m = [:]
		m[n.functional] = n.functional.accept(this)
		return actor.exit(n, m)
	}

	T visit(GroupExpr n) {
		actor.enter(n)
		Map<IVisitable, T> m = [:]
		m[n.expr] = n.expr.accept(this)
		return actor.exit(n, m)
	}

	T visit(VariableExpr n) {
		actor.enter(n)
		return actor.exit(n, [:])
	}
}
