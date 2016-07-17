package deepdoop.actions;

import deepdoop.datalog.*;
import deepdoop.datalog.clause.*;
import deepdoop.datalog.component.*;
import deepdoop.datalog.element.*;
import deepdoop.datalog.element.atom.*;
import deepdoop.datalog.expr.*;

public interface IVisitor<T> {
	T visit(Program n);

	T visit(Constraint n);
	T visit(Declaration n);
	T visit(RefModeDeclaration n);
	T visit(Rule n);

	T visit(CmdComponent n);
	T visit(Component n);

	T visit(AggregationElement n);
	T visit(ComparisonElement n);
	T visit(GroupElement n);
	T visit(LogicalElement n);
	T visit(NegationElement n);

	T visit(Directive n);
	T visit(Functional n);
	T visit(Predicate n);
	T visit(Primitive n);
	T visit(RefMode n);
	T visit(StubAtom n);

	T visit(BinaryExpr n);
	T visit(ConstantExpr n);
	T visit(FunctionalHeadExpr n);
	T visit(GroupExpr n);
	T visit(VariableExpr n);
}
