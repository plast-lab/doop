package org.clyze.deepdoop.actions

import org.clyze.deepdoop.datalog.Annotation
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
import org.clyze.deepdoop.datalog.element.atom.IAtom
import org.clyze.deepdoop.datalog.element.atom.Predicate
import org.clyze.deepdoop.datalog.element.atom.Primitive
import org.clyze.deepdoop.datalog.element.atom.RefMode
import org.clyze.deepdoop.datalog.element.atom.Stub
import org.clyze.deepdoop.datalog.expr.BinaryExpr
import org.clyze.deepdoop.datalog.expr.ConstantExpr
import org.clyze.deepdoop.datalog.expr.GroupExpr
import org.clyze.deepdoop.datalog.expr.IExpr
import org.clyze.deepdoop.datalog.expr.VariableExpr
import org.clyze.deepdoop.system.ErrorId
import org.clyze.deepdoop.system.ErrorManager

import static org.clyze.deepdoop.datalog.expr.ConstantExpr.Type.*

class TypeInferenceVisitingActor extends PostOrderVisitor<Void> implements IActor<Void>, TDummyActor<Void> {

	// Predicate Name x Parameter Index x Possible Types
	Map<String, Map<Integer, Set<String>>> possibleTypes = [:].withDefault { [:].withDefault { [] as Set } }
	// Variable x Possible Types (for current clause)
	Map<VariableExpr, Set<String>> varTypes = [:].withDefault { [] as Set }
	// Predicate Name x Variable x Index (for current clause)
	Map<String, Map<VariableExpr, Integer>> varIndices = [:].withDefault { [:] }
	// Misc Elements (e.g. constants) x Type (for current clause)
	Map<IVisitable, String> values = [:]

	// Implementing fix-point computation
	Set<Rule> oldDeltaRules = [] as Set
	Set<Rule> deltaRules = [] as Set

	InfoCollectionVisitingActor infoActor

	TypeInferenceVisitingActor(InfoCollectionVisitingActor infoActor) {
		// Implemented this way, because Java doesn't allow usage of "this"
		// keyword before all implicit/explicit calls to super/this have
		// returned
		super(null)
		actor = this
		this.infoActor = infoActor
	}

	Void visit(Component n) {
		oldDeltaRules = n.rules
		// Implemented this way because Groovy doesn't support do-while blocks
		while (true) {
			actor.enter(n)
			n.declarations.each { it.accept(this) }
			n.constraints.each { it.accept(this) }
			oldDeltaRules.each { it.accept(this) }
			actor.exit(n, null)

			if (deltaRules.isEmpty()) break
			oldDeltaRules = deltaRules
			deltaRules = [] as Set
		}
		possibleTypes.each { pred, map ->
			def types = map.collect { i, types -> coalesce(types, pred, i) }.join(" x ")
			println "$pred:\t$types"
		}
		null
	}

	Void exit(Program n, Map<IVisitable, Void> m) { null }

	Void exit(CmdComponent n, Map<IVisitable, Void> m) { null }

	Void exit(Component n, Map<IVisitable, Void> m) { null }

	Void exit(Constraint n, Map<IVisitable, Void> m) { null }

	void enter(Declaration n) {
		varTypes.clear()
		varIndices.clear()
		values.clear()
	}

	Void exit(Declaration n, Map<IVisitable, Void> m) {
		if (!n.annotations.any { it.kind == Annotation.Kind.ENTITY })
			n.types.eachWithIndex { type, i ->
				possibleTypes[n.atom.name][i] << type.name
			}
		null
	}

	Void exit(RefModeDeclaration n, Map<IVisitable, Void> m) { null }

	void enter(Rule n) {
		varTypes.clear()
		varIndices.clear()
		values.clear()
	}

	Void exit(Rule n, Map<IVisitable, Void> m) {
		n.head.elements.each {
			def predName = (it as IAtom).name
			varIndices[predName].each { var, i ->
				def types = varTypes[var]
				// Var might not have possible types yet
				if (!types.isEmpty()) {
					// Don't add to deltas if no new type is inferred
					def currentTypes = possibleTypes[predName][i]
					if (types.any { !(it in currentTypes) }) {
						possibleTypes[predName][i] += types
						// Add to deltas every rule that uses the current predicate in it's body
						deltaRules += infoActor.affectedRules[predName]
					}
				} else
					deltaRules << n
			}
		}
		null
	}

	Void exit(AggregationElement n, Map<IVisitable, Void> m) { null }

	Void exit(ComparisonElement n, Map<IVisitable, Void> m) {
		VariableExpr var
		if (n.expr.left instanceof VariableExpr) var = n.expr.left as VariableExpr
		else if (n.expr.right instanceof VariableExpr) var = n.expr.right as VariableExpr

		ConstantExpr value
		if (n.expr.left instanceof ConstantExpr) value = n.expr.left as ConstantExpr
		else if (n.expr.right instanceof ConstantExpr) value = n.expr.right as ConstantExpr

		if (var && value) varTypes[var] << values[value]
		null
	}

	Void exit(GroupElement n, Map<IVisitable, Void> m) { null }

	Void exit(LogicalElement n, Map<IVisitable, Void> m) { null }

	Void exit(NegationElement n, Map<IVisitable, Void> m) { null }

	Void exit(Constructor n, Map<IVisitable, Void> m) {
		//exit(n as Functional, m)
		varTypes[n.valueExpr as VariableExpr] << n.entity.name
		null
	}

	Void exit(Entity n, Map<IVisitable, Void> m) { null }

	Void exit(Functional n, Map<IVisitable, Void> m) {
		(n.keyExprs + n.valueExpr).eachWithIndex { expr, i -> varWithIndex(n.name, expr, i) }
		null
	}

	Void exit(Predicate n, Map<IVisitable, Void> m) {
		n.exprs.eachWithIndex { expr, i -> varWithIndex(n.name, expr, i) }
		null
	}

	Void exit(Primitive n, Map<IVisitable, Void> m) { null }

	Void exit(RefMode n, Map<IVisitable, Void> m) { null }

	Void exit(Stub n, Map<IVisitable, Void> m) { null }

	Void exit(BinaryExpr n, Map<IVisitable, Void> m) { null }

	Void exit(ConstantExpr n, Map<IVisitable, Void> m) {
		if (n.type == INTEGER) values[n] = "int"
		else if (n.type == STRING) values[n] = "string"
		null
	}

	Void exit(GroupExpr n, Map<IVisitable, Void> m) { null }

	Void exit(VariableExpr n, Map<IVisitable, Void> m) { null }

	private void varWithIndex(String name, IExpr expr, int i) {
		if (expr instanceof VariableExpr) {
			def var = expr as VariableExpr
			varIndices[name][var] = i
			if (name in possibleTypes && !var.isDontCare())
				varTypes[var] += possibleTypes[name][i]
		}
	}

	private String coalesce(Set<String> types, String predName, int index) {
		def resultTypes = [] as Set
		// Phase 1: Include types that don't have a better representative already in the set
		types.each { t ->
			def superTypes = infoActor.superTypes[t]
			if (!superTypes.any { it in types })
				resultTypes << t
		}

		String coalescedType

		// Phase 2: Find first common supertype for types in the same hierarchy
		if (resultTypes.size() != 1) {
			String t1
			resultTypes.each { t2 ->
				// First element
				if (!t1) {
					t1 = t2
					return
				}
				String superT
				def superTypesOfT1 = infoActor.superTypes[t1]
				def superTypesOfT2 = infoActor.superTypes[t2]
				superTypesOfT1.each {
					if (!superT && it in superTypesOfT2) superT = it
				}
				t1 = superT
			}
			// TODO: JUST A HACK (WRONG ONE)
			coalescedType = t1
		} else
			coalescedType = resultTypes.first()

		//if (resultTypes.size() != 1) {
		//	println resultTypes
		//	ErrorManager.error(ErrorId.INCOMPATIBLE_TYPES, predName, index)
		//}

		return coalescedType
	}
}
