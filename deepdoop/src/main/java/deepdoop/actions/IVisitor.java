package deepdoop.actions;

import deepdoop.datalog.*;
import deepdoop.datalog.clause.*;
import deepdoop.datalog.component.*;
import deepdoop.datalog.element.*;
import deepdoop.datalog.element.atom.*;
import deepdoop.datalog.expr.*;
import java.util.Map;

// Exit methods return an object of the same type as the visited node (might be
// null). The additional map argument accumulates the returned objects from all
// children nodes.
public interface IVisitor {
	default void enter(Program n)                                                        {}
	default Program exit(Program n, Map<IVisitable, IVisitable> m)                       { return n; }

	default void enter(Constraint n)                                                     {}
	default Constraint exit(Constraint n, Map<IVisitable, IVisitable> m)                 { return n; }
	default void enter(Declaration n)                                                    {}
	default Declaration exit(Declaration n, Map<IVisitable, IVisitable> m)               { return n; }
	default void enter(RefModeDeclaration n)                                             {}
	default RefModeDeclaration exit(RefModeDeclaration n, Map<IVisitable, IVisitable> m) { return n; }
	default void enter(Rule n)                                                           {}
	default Rule exit(Rule n, Map<IVisitable, IVisitable> m)                             { return n; }

	default void enter(CmdComponent n)                                                   {}
	default CmdComponent exit(CmdComponent n, Map<IVisitable, IVisitable> m)             { return n; }
	default void enter(Component n)                                                      {}
	default Component exit(Component n, Map<IVisitable, IVisitable> m)                   { return n; }

	default void enter(AggregationElement n)                                             {}
	default AggregationElement exit(AggregationElement n, Map<IVisitable, IVisitable> m) { return n; }
	default void enter(ComparisonElement n)                                              {}
	default ComparisonElement exit(ComparisonElement n, Map<IVisitable, IVisitable> m)   { return n; }
	default void enter(GroupElement n)                                                   {}
	default GroupElement exit(GroupElement n, Map<IVisitable, IVisitable> m)             { return n; }
	default void enter(LogicalElement n)                                                 {}
	default LogicalElement exit(LogicalElement n, Map<IVisitable, IVisitable> m)         { return n; }
	default void enter(NegationElement n)                                                {}
	default NegationElement exit(NegationElement n, Map<IVisitable, IVisitable> m)       { return n; }

	default void enter(Directive n)                                                      {}
	default Directive exit(Directive n, Map<IVisitable, IVisitable> m)                   { return n; }
	default void enter(Functional n)                                                     {}
	default Functional exit(Functional n, Map<IVisitable, IVisitable> m)                 { return n; }
	default void enter(Predicate n)                                                      {}
	default Predicate exit(Predicate n, Map<IVisitable, IVisitable> m)                   { return n; }
	default void enter(Primitive n)                                                      {}
	default Primitive exit(Primitive n, Map<IVisitable, IVisitable> m)                   { return n; }
	default void enter(RefMode n)                                                        {}
	default RefMode exit(RefMode n, Map<IVisitable, IVisitable> m)                       { return n; }
	default void enter(StubAtom n)                                                       {}
	default StubAtom exit(StubAtom n, Map<IVisitable, IVisitable> m)                     { return n; }

	default void enter(BinaryExpr n)                                                     {}
	default BinaryExpr exit(BinaryExpr n, Map<IVisitable, IVisitable> m)                 { return n; }
	default void enter(ConstantExpr n)                                                   {}
	default ConstantExpr exit(ConstantExpr n, Map<IVisitable, IVisitable> m)             { return n; }
	default void enter(FunctionalHeadExpr n)                                             {}
	default FunctionalHeadExpr exit(FunctionalHeadExpr n, Map<IVisitable, IVisitable> m) { return n; }
	default void enter(GroupExpr n)                                                      {}
	default GroupExpr exit(GroupExpr n, Map<IVisitable, IVisitable> m)                   { return n; }
	default void enter(VariableExpr n)                                                   {}
	default VariableExpr exit(VariableExpr n, Map<IVisitable, IVisitable> m)             { return n; }
}
