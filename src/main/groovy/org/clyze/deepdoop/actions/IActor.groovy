package org.clyze.deepdoop.actions

import org.clyze.deepdoop.datalog.Program
import org.clyze.deepdoop.datalog.clause.Constraint
import org.clyze.deepdoop.datalog.clause.Declaration
import org.clyze.deepdoop.datalog.clause.RefModeDeclaration
import org.clyze.deepdoop.datalog.clause.Rule
import org.clyze.deepdoop.datalog.component.CmdComponent
import org.clyze.deepdoop.datalog.component.Component
import org.clyze.deepdoop.datalog.element.*
import org.clyze.deepdoop.datalog.element.atom.*
import org.clyze.deepdoop.datalog.expr.BinaryExpr
import org.clyze.deepdoop.datalog.expr.ConstantExpr
import org.clyze.deepdoop.datalog.expr.GroupExpr
import org.clyze.deepdoop.datalog.expr.VariableExpr

interface IActor<T> {
	void enter(Program n)

	T exit(Program n, Map<IVisitable, T> m)

	void enter(CmdComponent n)

	T exit(CmdComponent n, Map<IVisitable, T> m)

	void enter(Component n)

	T exit(Component n, Map<IVisitable, T> m)

	void enter(Constraint n)

	T exit(Constraint n, Map<IVisitable, T> m)

	void enter(Declaration n)

	T exit(Declaration n, Map<IVisitable, T> m)

	void enter(RefModeDeclaration n)

	T exit(RefModeDeclaration n, Map<IVisitable, T> m)

	void enter(Rule n)

	T exit(Rule n, Map<IVisitable, T> m)

	void enter(AggregationElement n)

	T exit(AggregationElement n, Map<IVisitable, T> m)

	void enter(ComparisonElement n)

	T exit(ComparisonElement n, Map<IVisitable, T> m)

	void enter(GroupElement n)

	T exit(GroupElement n, Map<IVisitable, T> m)

	void enter(LogicalElement n)

	T exit(LogicalElement n, Map<IVisitable, T> m)

	void enter(NegationElement n)

	T exit(NegationElement n, Map<IVisitable, T> m)

	void enter(Constructor n)

	T exit(Constructor n, Map<IVisitable, T> m)

	void enter(Entity n)

	T exit(Entity n, Map<IVisitable, T> m)

	void enter(Functional n)

	T exit(Functional n, Map<IVisitable, T> m)

	void enter(Predicate n)

	T exit(Predicate n, Map<IVisitable, T> m)

	void enter(Primitive n)

	T exit(Primitive n, Map<IVisitable, T> m)

	void enter(RefMode n)

	T exit(RefMode n, Map<IVisitable, T> m)

	void enter(Stub n)

	T exit(Stub n, Map<IVisitable, T> m)

	void enter(BinaryExpr n)

	T exit(BinaryExpr n, Map<IVisitable, T> m)

	void enter(ConstantExpr n)

	T exit(ConstantExpr n, Map<IVisitable, T> m)

	void enter(GroupExpr n)

	T exit(GroupExpr n, Map<IVisitable, T> m)

	void enter(VariableExpr n)

	T exit(VariableExpr n, Map<IVisitable, T> m)
}
