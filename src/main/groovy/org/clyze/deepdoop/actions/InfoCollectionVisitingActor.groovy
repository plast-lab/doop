package org.clyze.deepdoop.actions

import org.clyze.deepdoop.datalog.Annotation
import org.clyze.deepdoop.datalog.Program
import org.clyze.deepdoop.datalog.clause.Constraint
import org.clyze.deepdoop.datalog.clause.Declaration
import org.clyze.deepdoop.datalog.clause.RefModeDeclaration
import org.clyze.deepdoop.datalog.clause.Rule
import org.clyze.deepdoop.datalog.component.Component
import org.clyze.deepdoop.datalog.element.*
import org.clyze.deepdoop.datalog.element.atom.*
import org.clyze.deepdoop.datalog.expr.BinaryExpr
import org.clyze.deepdoop.datalog.expr.GroupExpr
import org.clyze.deepdoop.datalog.expr.VariableExpr

class InfoCollectionVisitingActor extends PostOrderVisitor<IVisitable> implements IActor<IVisitable>, TDummyActor<IVisitable> {

	Map<IVisitable, List<IAtom>> declaringAtoms = [:].withDefault { [] }
	Map<IVisitable, List<IAtom>> usedAtoms = [:].withDefault { [] }
	Map<IVisitable, List<VariableExpr>> vars = [:].withDefault { [] }

	Set<String> allTypes = [] as Set
	Map<String, String> directSuperType = [:]
	Map<String, Set<String>> superTypesOrdered = [:]
	List<String> allConstructors = []
	Map<String, String> constructorBaseType = [:]

	// Predicate Name x Set of Rules
	Map<String, Set<Rule>> affectedRules = [:].withDefault { [] as Set }

	InfoCollectionVisitingActor() {
		// Implemented this way, because Java doesn't allow usage of "this"
		// keyword before all implicit/explicit calls to super/this have
		// returned
		super(null)
		actor = this
	}

	IVisitable exit(Program n, Map m) {
		declaringAtoms[n] = declaringAtoms[n.globalComp] + (n.comps.values().collect {
			declaringAtoms[it]
		}.flatten() as List<IAtom>)
		usedAtoms[n] = usedAtoms[n.globalComp] +
				(n.comps.collect { usedAtoms[it.value] }.flatten() as List<IAtom>)

		// Base case for supertypes
		allTypes.each { type ->
			def superType = directSuperType[type]
			superTypesOrdered[type] = (superType ? [superType] : []) as Set
		}
		// Transitive closure on supertypes
		def oldDeltaTypes = superTypesOrdered.keySet()
		while(!oldDeltaTypes.isEmpty()) {
			def deltaTypes = [] as Set
			oldDeltaTypes.each { type ->
				def newSuperTypes = [] as Set
				superTypesOrdered[type].each { superType -> newSuperTypes += superTypesOrdered[superType] }
				if (superTypesOrdered[type].addAll(newSuperTypes)) deltaTypes << type
			}
			oldDeltaTypes = deltaTypes
		}

		return n
	}

	/*
	IVisitable exit(CmdComponent n, Map m) {
		Map<String, IAtom> declMap = [:]
		n.declarations.each { declMap << getDeclaringAtoms(it) }
		declaringAtoms[n] = declMap

		Set<String> importPreds = [] as Set
		n.imports.each { p ->
			def pName = p.name
			importPreds << pName
			if (declMap[pName] == null)
				ErrorManager.error(ErrorId.CMD_NO_DECL, pName)
		}
		declMap.keySet().each { declName ->
			if (!(declName in importPreds))
				ErrorManager.error(ErrorId.CMD_NO_IMPORT, declName)
		}
		Map<String, IAtom> usedMap = [:]
		n.exports.each { usedMap[it.name] = it }
		usedAtoms[n] = usedMap
		null
	}
	*/

	IVisitable exit(Component n, Map m) {
		declaringAtoms[n] = (n.declarations + n.rules).collect { declaringAtoms[it] }.flatten() as List<IAtom>
		usedAtoms[n] = (n.declarations + n.constraints + n.rules).collect { usedAtoms[it] }.flatten() as List<IAtom>
		null
	}

	IVisitable exit(Constraint n, Map m) {
		usedAtoms[n] = usedAtoms[n.head] + usedAtoms[n.body]
		null
	}

	IVisitable exit(Declaration n, Map m) {
		declaringAtoms[n] = [n.atom]
		usedAtoms[n] = n.types

		if (n.annotations.any { it.kind == Annotation.Kind.ENTITY }) {
			def type = n.atom.name
			allTypes << type
			if (!n.types.isEmpty()) directSuperType[type] = n.types.first().name
		}

		if (n.annotations.any { it.kind == Annotation.Kind.CONSTRUCTOR })
			constructorBaseType[n.atom.name] = n.types.last().name

		null
	}

	IVisitable exit(RefModeDeclaration n, Map m) {
		declaringAtoms[n] = [n.atom, n.types.first()]
		null
	}

	IVisitable exit(Rule n, Map m) {
		// Atoms used in the head, are declared in the rule
		declaringAtoms[n] = usedAtoms[n.head]
		usedAtoms[n] = usedAtoms[n.body]
		// Atoms that appear in the head of the rule but have the @past stage
		// are external
		//declMap.findAll { name, atom -> atom.stage == "@past" }
		//		.each { name, atom ->
		//	declMap.remove(name)
		//	usedMap[name] = atom
		//}
		//def headPredicates = n.head.elements
		//		.findAll { !(it instanceof Constructor) }
		//		.collect { (it as IAtom).name }
		n.body.elements
				.findAll { it instanceof IAtom }
				.each { affectedRules[(it as IAtom).name] << n }
		null
	}

	IVisitable exit(AggregationElement n, Map m) {
		usedAtoms[n] = usedAtoms[n.body]

		vars[n] = vars[n.body]
		null
	}

	IVisitable exit(ComparisonElement n, Map m) {
		vars[n] = vars[n.expr]
		null
	}

	IVisitable exit(GroupElement n, Map m) {
		usedAtoms[n] = usedAtoms[n.element]

		vars[n] = vars[n.element]
		null
	}

	IVisitable exit(LogicalElement n, Map m) {
		usedAtoms[n] = n.elements.collect { usedAtoms[it] }.flatten() as List<IAtom>

		vars[n] = n.elements.collect { vars[it] }.flatten() as List<VariableExpr>
		null
	}

	IVisitable exit(NegationElement n, Map m) {
		usedAtoms[n] = usedAtoms[n.element]

		vars[n] = vars[n.element]
		null
	}

	IVisitable exit(Constructor n, Map m) {
		exit(n as Functional, m)
		allConstructors << n.name
		null
	}

	IVisitable exit(Entity n, Map m) {
		exit(n as Predicate, m)
	}

	IVisitable exit(Functional n, Map m) {
		usedAtoms[n] = [n]

		vars[n] = (n.keyExprs + [n.valueExpr]).collect { vars[it] }.flatten() as List<VariableExpr>
		null
	}

	IVisitable exit(Predicate n, Map m) {
		usedAtoms[n] = [n]

		vars[n] = n.exprs.collect { vars[it] }.flatten() as List<VariableExpr>
		null
	}

	IVisitable exit(Primitive n, Map m) {
		vars[n] = [n.var]
		null
	}

	IVisitable exit(RefMode n, Map m) {
		usedAtoms[n] = [n]

		vars[n] = [n.entityVar] + vars[n.valueExpr]
		null
	}

	IVisitable exit(BinaryExpr n, Map m) {
		vars[n] = vars[n.left] + vars[n.right]
		null
	}

	IVisitable exit(GroupExpr n, Map m) {
		vars[n] = vars[n.expr]
		null
	}

	IVisitable exit(VariableExpr n, Map m) {
		vars[n] = [n]
		null
	}
}
