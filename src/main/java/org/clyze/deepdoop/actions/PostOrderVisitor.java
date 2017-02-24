package org.clyze.deepdoop.actions;

import java.util.HashMap;
import java.util.Map;
import org.clyze.deepdoop.datalog.*;
import org.clyze.deepdoop.datalog.clause.*;
import org.clyze.deepdoop.datalog.component.*;
import org.clyze.deepdoop.datalog.element.*;
import org.clyze.deepdoop.datalog.element.atom.*;
import org.clyze.deepdoop.datalog.expr.*;

public class PostOrderVisitor<T> implements IVisitor<T> {

	protected IActor<T> _actor;

	public PostOrderVisitor(IActor<T> actor) {
		_actor = actor;
	}


	@Override
	public T visit(Program n) {
		_actor.enter(n);
		Map<IVisitable, T> m = new HashMap<>();
		m.put(n.globalComp, n.globalComp.accept(this));
		for (Component c : n.comps.values()) m.put(c, c.accept(this));
		return _actor.exit(n, m);
	}

	@Override
	public T visit(CmdComponent n) {
		_actor.enter(n);
		Map<IVisitable, T> m = new HashMap<>();
		for (StubAtom p : n.exports)         m.put(p, p.accept(this));
		for (StubAtom p : n.imports)         m.put(p, p.accept(this));
		for (Declaration d : n.declarations) m.put(d, d.accept(this));
		for (Rule r : n.rules)               m.put(r, r.accept(this));
		return _actor.exit(n, m);
	}
	@Override
	public T visit(Component n) {
		_actor.enter(n);
		Map<IVisitable, T> m = new HashMap<>();
		for (Declaration d : n.declarations) m.put(d, d.accept(this));
		for (Constraint c : n.constraints)   m.put(c, c.accept(this));
		for (Rule r : n.rules)               m.put(r, r.accept(this));
		return _actor.exit(n, m);
	}

	@Override
	public T visit(Constraint n) {
		_actor.enter(n);
		Map<IVisitable, T> m = new HashMap<>();
		m.put(n.head, n.head.accept(this));
		m.put(n.body, n.body.accept(this));
		return _actor.exit(n, m);
	}
	@Override
	public T visit(Declaration n) {
		_actor.enter(n);
		Map<IVisitable, T> m = new HashMap<>();
		m.put(n.atom, n.atom.accept(this));
		for (IAtom t : n.types) m.put(t, t.accept(this));
		return _actor.exit(n, m);
	}
	@Override
	public T visit(RefModeDeclaration n) {
		_actor.enter(n);
		Map<IVisitable, T> m = new HashMap<>();
		m.put(n.atom, n.atom.accept(this));
		for (IAtom t : n.types) m.put(t, t.accept(this));
		return _actor.exit(n, m);
	}
	@Override
	public T visit(Rule n) {
		_actor.enter(n);
		Map<IVisitable, T> m = new HashMap<>();
		m.put(n.head, n.head.accept(this));
		if (n.body != null) m.put(n.body, n.body.accept(this));
		return _actor.exit(n, m);
	}

	@Override
	public T visit(AggregationElement n) {
		_actor.enter(n);
		Map<IVisitable, T> m = new HashMap<>();
		m.put(n.var, n.var.accept(this));
		m.put(n.predicate, n.predicate.accept(this));
		m.put(n.body, n.body.accept(this));
		return _actor.exit(n, m);
	}
	@Override
	public T visit(ComparisonElement n) {
		_actor.enter(n);
		Map<IVisitable, T> m = new HashMap<>();
		m.put(n.expr, n.expr.accept(this));
		return _actor.exit(n, m);
	}
	@Override
	public T visit(GroupElement n) {
		_actor.enter(n);
		Map<IVisitable, T> m = new HashMap<>();
		m.put(n.element, n.element.accept(this));
		return _actor.exit(n, m);
	}
	@Override
	public T visit(LogicalElement n) {
		_actor.enter(n);
		Map<IVisitable, T> m = new HashMap<>();
		for (IElement e : n.elements) m.put(e, e.accept(this));
		return _actor.exit(n, m);
	}
	@Override
	public T visit(NegationElement n) {
		_actor.enter(n);
		Map<IVisitable, T> m = new HashMap<>();
		m.put(n.element, n.element.accept(this));
		return _actor.exit(n, m);
	}

	@Override
	public T visit(Directive n) {
		_actor.enter(n);
		Map<IVisitable, T> m = new HashMap<>();
		if (n.backtick != null) m.put(n.backtick, n.backtick.accept(this));
		if (n.constant != null) m.put(n.constant, n.constant.accept(this));
		return _actor.exit(n, m);
	}
	@Override
	public T visit(Functional n) {
		_actor.enter(n);
		Map<IVisitable, T> m = new HashMap<>();
		for (IExpr e : n.keyExprs) m.put(e, e.accept(this));
		if (n.valueExpr != null) m.put(n.valueExpr, n.valueExpr.accept(this));
		return _actor.exit(n, m);
	}
	@Override
	public T visit(Predicate n) {
		_actor.enter(n);
		Map<IVisitable, T> m = new HashMap<>();
		for (IExpr e : n.exprs) m.put(e, e.accept(this));
		return _actor.exit(n, m);
	}
	@Override
	public T visit(Entity n) {
		_actor.enter(n);
		Map<IVisitable, T> m = new HashMap<>();
		for (IExpr e : n.exprs) m.put(e, e.accept(this));
		return _actor.exit(n, m);
	}
	@Override
	public T visit(Primitive n) {
		_actor.enter(n);
		Map<IVisitable, T> m = new HashMap<>();
		m.put(n.var, n.var.accept(this));
		return _actor.exit(n, m);
	}
	@Override
	public T visit(RefMode n) {
		_actor.enter(n);
		Map<IVisitable, T> m = new HashMap<>();
		m.put(n.entityVar, n.entityVar.accept(this));
		m.put(n.valueExpr, n.valueExpr.accept(this));
		return _actor.exit(n, m);
	}
	@Override
	public T visit(StubAtom n) {
		_actor.enter(n);
		return _actor.exit(n, new HashMap<>());
	}

	@Override
	public T visit(BinaryExpr n) {
		_actor.enter(n);
		Map<IVisitable, T> m = new HashMap<>();
		m.put(n.left, n.left.accept(this));
		m.put(n.right, n.right.accept(this));
		return _actor.exit(n, m);
	}
	@Override
	public T visit(ConstantExpr n) {
		_actor.enter(n);
		return _actor.exit(n, new HashMap<>());
	}
	@Override
	public T visit(FunctionalHeadExpr n) {
		_actor.enter(n);
		Map<IVisitable, T> m = new HashMap<>();
		m.put(n.functional, n.functional.accept(this));
		return _actor.exit(n, m);
	}
	@Override
	public T visit(GroupExpr n) {
		_actor.enter(n);
		Map<IVisitable, T> m = new HashMap<>();
		m.put(n.expr, n.expr.accept(this));
		return _actor.exit(n, m);
	}
	@Override
	public T visit(VariableExpr n) {
		_actor.enter(n);
		return _actor.exit(n, new HashMap<>());
	}
}
