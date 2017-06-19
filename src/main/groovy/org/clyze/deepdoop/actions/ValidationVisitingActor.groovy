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

import static org.clyze.deepdoop.datalog.expr.ConstantExpr.Type.BOOLEAN
import static org.clyze.deepdoop.datalog.expr.ConstantExpr.Type.REAL

class ValidationVisitingActor extends PostOrderVisitor<IVisitable> implements IActor<IVisitable>, TDummyActor<IVisitable>  {

	InfoCollectionVisitingActor infoActor

	ValidationVisitingActor(InfoCollectionVisitingActor infoActor) {
		// Implemented this way, because Java doesn't allow usage of "this"
		// keyword before all implicit/explicit calls to super/this have
		// returned
		super(null)
		actor = this
		this.infoActor = infoActor
	}

	IVisitable exit(Program n, Map m) { n }

	IVisitable exit(CmdComponent n, Map m) { null }

	IVisitable exit(Component n, Map m) { null }

	IVisitable exit(Constraint n, Map m) { null }

	IVisitable exit(Declaration n, Map m) {
		n.types.findAll { !(it instanceof Primitive) }
				.findAll { !(it.name in infoActor.allTypes) }
				.each { ErrorManager.error(ErrorId.UNKNOWN_TYPE, it.name) }
		null
	}

	IVisitable exit(RefModeDeclaration n, Map m) { null }

	IVisitable exit(Rule n, Map m) {
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

	IVisitable exit(AggregationElement n, Map m) { null }

	IVisitable exit(ComparisonElement n, Map m) { null }

	IVisitable exit(GroupElement n, Map m) { null }

	IVisitable exit(LogicalElement n, Map m) { null }

	IVisitable exit(NegationElement n, Map m) { null }

	IVisitable exit(Constructor n, Map m) {
		if (!(n.entity.name in infoActor.allTypes))
			ErrorManager.error(ErrorId.UNKNOWN_TYPE, n.entity.name)

		def baseType = infoActor.constructorBaseType[n.name]
		// TODO should be more general check for predicates
		if (!baseType)
			ErrorManager.error(ErrorId.CONSTRUCTOR_UNKNOWN, n.name)
		if (n.entity.name != baseType && !(baseType in infoActor.superTypesOrdered[n.entity.name]))
			ErrorManager.error(ErrorId.CONSTRUCTOR_INCOMPATIBLE, n.name, n.entity.name)

		null
	}

	IVisitable exit(Entity n, Map m) { null }

	IVisitable exit(Functional n, Map m) {
		if (n.name.endsWith("__pArTiAl"))
			ErrorManager.error(ErrorId.RESERVED_SUFFIX)
		null
	}

	IVisitable exit(Predicate n, Map m) {
		if (n.name.endsWith("__pArTiAl"))
			ErrorManager.error(ErrorId.RESERVED_SUFFIX)
		null
	}

	IVisitable exit(Primitive n, Map m) { null }

	IVisitable exit(RefMode n, Map m) { null }

	IVisitable exit(Stub n, Map m) { null }

	IVisitable exit(BinaryExpr n, Map m) { null }

	IVisitable exit(ConstantExpr n, Map m) {
		if (n.type == REAL || n.type == BOOLEAN)
			ErrorManager.error(ErrorId.UNSUPPORTED_TYPE, n.type as String)
		null
	}

	IVisitable exit(GroupExpr n, Map m) { null }

	IVisitable exit(VariableExpr n, Map m) { null }
}
