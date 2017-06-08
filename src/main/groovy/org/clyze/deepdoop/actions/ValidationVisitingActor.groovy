package org.clyze.deepdoop.actions

import org.clyze.deepdoop.datalog.Program
import org.clyze.deepdoop.datalog.clause.Constraint
import org.clyze.deepdoop.datalog.clause.Declaration
import org.clyze.deepdoop.datalog.clause.RefModeDeclaration
import org.clyze.deepdoop.datalog.clause.Rule
import org.clyze.deepdoop.datalog.component.CmdComponent
import org.clyze.deepdoop.datalog.component.Component
import org.clyze.deepdoop.datalog.element.AggregationElement
import org.clyze.deepdoop.datalog.element.ComparisonElement
import org.clyze.deepdoop.datalog.element.GroupElement
import org.clyze.deepdoop.datalog.element.LogicalElement
import org.clyze.deepdoop.datalog.element.NegationElement
import org.clyze.deepdoop.datalog.element.atom.Constructor
import org.clyze.deepdoop.datalog.element.atom.Entity
import org.clyze.deepdoop.datalog.element.atom.Functional
import org.clyze.deepdoop.datalog.element.atom.Predicate
import org.clyze.deepdoop.datalog.element.atom.Primitive
import org.clyze.deepdoop.datalog.element.atom.RefMode
import org.clyze.deepdoop.datalog.element.atom.Stub
import org.clyze.deepdoop.datalog.expr.BinaryExpr
import org.clyze.deepdoop.datalog.expr.ConstantExpr
import org.clyze.deepdoop.datalog.expr.GroupExpr
import org.clyze.deepdoop.datalog.expr.VariableExpr
import org.clyze.deepdoop.system.ErrorId
import org.clyze.deepdoop.system.ErrorManager

import java.util.function.Function

import static org.clyze.deepdoop.datalog.expr.ConstantExpr.Type.BOOLEAN
import static org.clyze.deepdoop.datalog.expr.ConstantExpr.Type.REAL

class ValidationVisitingActor extends PostOrderVisitor<Void> implements IActor<Void>, TDummyActor<Void>  {

	InfoCollectionVisitingActor infoActor

	ValidationVisitingActor(InfoCollectionVisitingActor infoActor) {
		// Implemented this way, because Java doesn't allow usage of "this"
		// keyword before all implicit/explicit calls to super/this have
		// returned
		super(null)
		actor = this
		this.infoActor = infoActor
	}

	Void exit(Program n, Map<IVisitable, Void> m) { null }

	Void exit(CmdComponent n, Map<IVisitable, Void> m) { null }

	Void exit(Component n, Map<IVisitable, Void> m) { null }

	Void exit(Constraint n, Map<IVisitable, Void> m) { null }

	Void exit(Declaration n, Map<IVisitable, Void> m) {
		n.types.findAll { !(it instanceof Primitive) }
				.findAll { !(it.name in infoActor.allTypes) }
				.each { ErrorManager.error(ErrorId.UNKNOWN_TYPE, it.name) }
		null
	}

	Void exit(RefModeDeclaration n, Map<IVisitable, Void> m) { null }

	Void exit(Rule n, Map<IVisitable, Void> m) {
		def varsInHead = infoActor.vars[n.head]
		def varsInBody = infoActor.vars[n.body]
		varsInBody.findAll { !it.isDontCare() }
				.findAll { !varsInHead.contains(it) }
				.findAll { Collections.frequency(varsInBody, it) == 1 }
				.each { ErrorManager.warn(ErrorId.UNUSED_VAR, it.name) }

		n.head.elements.findAll { it instanceof Functional && !(it instanceof Constructor) }
				.findAll { (it as Functional).name in infoActor.allConstructors }
				.each { ErrorManager.error(ErrorId.CONSTRUCTOR_RULE, (it as Functional).name) }
		null
	}

	Void exit(AggregationElement n, Map<IVisitable, Void> m) { null }

	Void exit(ComparisonElement n, Map<IVisitable, Void> m) { null }

	Void exit(GroupElement n, Map<IVisitable, Void> m) { null }

	Void exit(LogicalElement n, Map<IVisitable, Void> m) { null }

	Void exit(NegationElement n, Map<IVisitable, Void> m) { null }

	Void exit(Constructor n, Map<IVisitable, Void> m) {
		if (!(n.entity.name in infoActor.allTypes))
			ErrorManager.error(ErrorId.UNKNOWN_TYPE, n.entity.name)
		null
	}

	Void exit(Entity n, Map<IVisitable, Void> m) { null }

	Void exit(Functional n, Map<IVisitable, Void> m) { null }

	Void exit(Predicate n, Map<IVisitable, Void> m) { null }

	Void exit(Primitive n, Map<IVisitable, Void> m) { null }

	Void exit(RefMode n, Map<IVisitable, Void> m) { null }

	Void exit(Stub n, Map<IVisitable, Void> m) { null }

	Void exit(BinaryExpr n, Map<IVisitable, Void> m) { null }

	Void exit(ConstantExpr n, Map<IVisitable, Void> m) {
		if (n.type == REAL || n.type == BOOLEAN) ErrorManager.error(ErrorId.UNSUPPORTED_TYPE, n.type as String)
		null
	}

	Void exit(GroupExpr n, Map<IVisitable, Void> m) { null }

	Void exit(VariableExpr n, Map<IVisitable, Void> m) { null }
}
