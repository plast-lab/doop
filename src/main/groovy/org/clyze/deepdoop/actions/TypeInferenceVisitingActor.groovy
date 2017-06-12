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

class TypeInferenceVisitingActor extends PostOrderVisitor<IVisitable> implements IActor<IVisitable>, TDummyActor<IVisitable> {

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

	IVisitable visit(Component n) {
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

	IVisitable exit(Program n, Map m) { n }

	IVisitable exit(CmdComponent n, Map m) { null }

	IVisitable exit(Component n, Map m) { null }

	IVisitable exit(Constraint n, Map m) { null }

	void enter(Declaration n) {
		varTypes.clear()
		varIndices.clear()
		values.clear()
	}

	IVisitable exit(Declaration n, Map m) {
		if (!n.annotations.any { it.kind == Annotation.Kind.ENTITY })
			n.types.eachWithIndex { type, i ->
				possibleTypes[n.atom.name][i] << type.name
			}
		null
	}

	IVisitable exit(RefModeDeclaration n, Map m) { null }

	void enter(Rule n) {
		varTypes.clear()
		varIndices.clear()
		values.clear()
	}

	IVisitable exit(Rule n, Map m) {
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

	IVisitable exit(AggregationElement n, Map m) { null }

	IVisitable exit(ComparisonElement n, Map m) {
		VariableExpr var
		if (n.expr.left instanceof VariableExpr) var = n.expr.left as VariableExpr
		else if (n.expr.right instanceof VariableExpr) var = n.expr.right as VariableExpr

		ConstantExpr value
		if (n.expr.left instanceof ConstantExpr) value = n.expr.left as ConstantExpr
		else if (n.expr.right instanceof ConstantExpr) value = n.expr.right as ConstantExpr

		if (var && value) varTypes[var] << values[value]
		null
	}

	IVisitable exit(GroupElement n, Map m) { null }

	IVisitable exit(LogicalElement n, Map m) { null }

	IVisitable exit(NegationElement n, Map m) { null }

	IVisitable exit(Constructor n, Map m) {
		//exit(n as Functional, m)
		varTypes[n.valueExpr as VariableExpr] << n.entity.name
		null
	}

	IVisitable exit(Entity n, Map m) { null }

	IVisitable exit(Functional n, Map m) {
		(n.keyExprs + n.valueExpr).eachWithIndex { expr, i -> varWithIndex(n.name, expr, i) }
		null
	}

	IVisitable exit(Predicate n, Map m) {
		n.exprs.eachWithIndex { expr, i -> varWithIndex(n.name, expr, i) }
		null
	}

	IVisitable exit(Primitive n, Map m) { null }

	IVisitable exit(RefMode n, Map m) { null }

	IVisitable exit(Stub n, Map m) { null }

	IVisitable exit(BinaryExpr n, Map m) { null }

	IVisitable exit(ConstantExpr n, Map m) {
		if (n.type == INTEGER) values[n] = "int"
		else if (n.type == STRING) values[n] = "string"
		null
	}

	IVisitable exit(GroupExpr n, Map m) { null }

	IVisitable exit(VariableExpr n, Map m) { null }

	private void varWithIndex(String name, IExpr expr, int i) {
		if (expr instanceof VariableExpr) {
			def var = expr as VariableExpr
			varIndices[name][var] = i
			if (name in possibleTypes && !var.isDontCare())
				varTypes[var] += possibleTypes[name][i]
		}
	}

	private String coalesce(Set<String> types, String predName, int index) {
		def resultTypes = []
		// Phase 1: Include types that don't have a better representative already in the set
		types.each { t ->
			def superTypes = infoActor.superTypesOrdered[t]
			if (!superTypes.any { it in types }) resultTypes << t
		}
		String coalescedType

		// Phase 2: Find first common supertype for types in the same hierarchy
		if (resultTypes.size() != 1) {
			String t1 = resultTypes[0]
			resultTypes.removeAt(0)
			while(resultTypes) {
				// Iterate types in pairs
				String t2 = resultTypes[0]
				resultTypes.removeAt(0)

				def superTypesOfT1 = infoActor.superTypesOrdered[t1]
				def superTypesOfT2 = infoActor.superTypesOrdered[t2]
				// Move upwards in the hierarchy until a common type is found
				String superT
				superTypesOfT1.each {
					if (!superT && it in superTypesOfT2) superT = it
				}
				t1 = superT

				if (!superT)
					ErrorManager.error(ErrorId.INCOMPATIBLE_TYPES, predName, index)
			}
			coalescedType = t1
		} else
			coalescedType = resultTypes.first()

		return coalescedType
	}
}
