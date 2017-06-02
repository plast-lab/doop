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

interface IVisitor<T> {
	T visit(Program n)

	T visit(CmdComponent n)

	T visit(Component n)

	T visit(Constraint n)

	T visit(Declaration n)

	T visit(RefModeDeclaration n)

	T visit(Rule n)

	T visit(AggregationElement n)

	T visit(ComparisonElement n)

	T visit(GroupElement n)

	T visit(LogicalElement n)

	T visit(NegationElement n)

	T visit(Constructor n)

	T visit(Entity n)

	T visit(Functional n)

	T visit(Predicate n)

	T visit(Primitive n)

	T visit(RefMode n)

	T visit(Stub n)

	T visit(BinaryExpr n)

	T visit(ConstantExpr n)

	T visit(GroupExpr n)

	T visit(VariableExpr n)
}
