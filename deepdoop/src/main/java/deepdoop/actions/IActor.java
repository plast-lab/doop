package deepdoop.actions;

import deepdoop.datalog.*;
import deepdoop.datalog.clause.*;
import deepdoop.datalog.component.*;
import deepdoop.datalog.element.*;
import deepdoop.datalog.element.atom.*;
import deepdoop.datalog.expr.*;
import java.util.Map;

// Exit methods return an object of some type T. The additional map argument
// accumulates such objects from all children nodes.
public interface IActor<T> {
	default void enter(Program n)                              {}
	default T exit(Program n, Map<IVisitable, T> m)            { return null; }

	default void enter(Constraint n)                           {}
	default T exit(Constraint n, Map<IVisitable, T> m)         { return null; }
	default void enter(Declaration n)                          {}
	default T exit(Declaration n, Map<IVisitable, T> m)        { return null; }
	default void enter(RefModeDeclaration n)                   {}
	default T exit(RefModeDeclaration n, Map<IVisitable, T> m) { return null; }
	default void enter(Rule n)                                 {}
	default T exit(Rule n, Map<IVisitable, T> m)               { return null; }

	default void enter(CmdComponent n)                         {}
	default T exit(CmdComponent n, Map<IVisitable, T> m)       { return null; }
	default void enter(Component n)                            {}
	default T exit(Component n, Map<IVisitable, T> m)          { return null; }

	default void enter(AggregationElement n)                   {}
	default T exit(AggregationElement n, Map<IVisitable, T> m) { return null; }
	default void enter(ComparisonElement n)                    {}
	default T exit(ComparisonElement n, Map<IVisitable, T> m)  { return null; }
	default void enter(GroupElement n)                         {}
	default T exit(GroupElement n, Map<IVisitable, T> m)       { return null; }
	default void enter(LogicalElement n)                       {}
	default T exit(LogicalElement n, Map<IVisitable, T> m)     { return null; }
	default void enter(NegationElement n)                      {}
	default T exit(NegationElement n, Map<IVisitable, T> m)    { return null; }

	default void enter(Directive n)                            {}
	default T exit(Directive n, Map<IVisitable, T> m)          { return null; }
	default void enter(Functional n)                           {}
	default T exit(Functional n, Map<IVisitable, T> m)         { return null; }
	default void enter(Predicate n)                            {}
	default T exit(Predicate n, Map<IVisitable, T> m)          { return null; }
	default void enter(Primitive n)                            {}
	default T exit(Primitive n, Map<IVisitable, T> m)          { return null; }
	default void enter(RefMode n)                              {}
	default T exit(RefMode n, Map<IVisitable, T> m)            { return null; }
	default void enter(StubAtom n)                             {}
	default T exit(StubAtom n, Map<IVisitable, T> m)           { return null; }

	default void enter(BinaryExpr n)                           {}
	default T exit(BinaryExpr n, Map<IVisitable, T> m)         { return null; }
	default void enter(ConstantExpr n)                         {}
	default T exit(ConstantExpr n, Map<IVisitable, T> m)       { return null; }
	default void enter(FunctionalHeadExpr n)                   {}
	default T exit(FunctionalHeadExpr n, Map<IVisitable, T> m) { return null; }
	default void enter(GroupExpr n)                            {}
	default T exit(GroupExpr n, Map<IVisitable, T> m)          { return null; }
	default void enter(VariableExpr n)                         {}
	default T exit(VariableExpr n, Map<IVisitable, T> m)       { return null; }
}
