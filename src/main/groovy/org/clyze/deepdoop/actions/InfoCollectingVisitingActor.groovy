package org.clyze.deepdoop.actions

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

class InfoCollectingVisitingActor extends PostOrderVisitor<Void> implements IActor<Void>, TDummyActor<Void> {

	Map<IVisitable, List<IAtom>> declaringAtoms = [:].withDefault { [] }
	Map<IVisitable, List<IAtom>> usedAtoms = [:].withDefault { [] }
	Map<IVisitable, List<VariableExpr>> vars = [:].withDefault { [] }

	InfoCollectingVisitingActor() {
		// Implemented this way, because Java doesn't allow usage of "this"
		// keyword before all implicit/explicit calls to super/this have
		// returned
		super(null)
		actor = this
	}

	Void exit(Program n, Map m) {
		declaringAtoms[n] = declaringAtoms[n.globalComp] + (n.comps.values().collect {
			declaringAtoms[it]
		}.flatten() as List<IAtom>)
		usedAtoms[n] = usedAtoms[n.globalComp] + (n.comps.values().collect { usedAtoms[it] }.flatten() as List<IAtom>)
		null
	}

	/*
	Void exit(CmdComponent n, Map m) {
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

	Void exit(Component n, Map m) {
		declaringAtoms[n] = (n.declarations + n.rules).collect { declaringAtoms[it] }.flatten() as List<IAtom>
		usedAtoms[n] = (n.declarations + n.constraints + n.rules).collect { usedAtoms[it] }.flatten() as List<IAtom>
		null
	}

	Void exit(Constraint n, Map m) {
		usedAtoms[n] = usedAtoms[n.head] + usedAtoms[n.body]
		null
	}

	Void exit(Declaration n, Map m) {
		declaringAtoms[n] = [n.atom]
		usedAtoms[n] = n.types
		null
	}

	Void exit(RefModeDeclaration n, Map m) {
		declaringAtoms[n] = [n.atom, n.types.first()]
		null
	}

	Void exit(Rule n, Map m) {
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
		null
	}

	Void exit(AggregationElement n, Map m) {
		usedAtoms[n] = usedAtoms[n.body]

		vars[n] = vars[n.body]
		null
	}

	Void exit(ComparisonElement n, Map m) {
		vars[n] = vars[n.expr]
		null
	}

	Void exit(GroupElement n, Map m) {
		usedAtoms[n] = usedAtoms[n.element]

		vars[n] = vars[n.element]
		null
	}

	Void exit(LogicalElement n, Map m) {
		usedAtoms[n] = n.elements.collect { usedAtoms[it] }.flatten() as List<IAtom>

		vars[n] = n.elements.collect { vars[it] }.flatten() as List<VariableExpr>
		null
	}

	Void exit(NegationElement n, Map m) {
		usedAtoms[n] = usedAtoms[n.element]

		vars[n] = vars[n.element]
		null
	}

	Void exit(Constructor n, Map m) {
		exit(n as Functional, m)
		null
	}

	Void exit(Entity n, Map m) {
		exit(n as Predicate, m)
	}

	Void exit(Functional n, Map m) {
		usedAtoms[n] = [n]

		vars[n] = (n.keyExprs + [n.valueExpr]).collect { vars[it] }.flatten() as List<VariableExpr>
		null
	}

	Void exit(Predicate n, Map m) {
		usedAtoms[n] = [n]

		vars[n] = n.exprs.collect { vars[it] }.flatten() as List<VariableExpr>
		null
	}

	Void exit(Primitive n, Map m) {
		vars[n] = [n.var]
		null
	}

	Void exit(RefMode n, Map m) {
		usedAtoms[n] = [n]

		vars[n] = [n.entityVar] + vars[n.valueExpr]
		null
	}

	Void exit(BinaryExpr n, Map m) {
		vars[n] = vars[n.left] + vars[n.right]
		null
	}

	Void exit(GroupExpr n, Map m) {
		vars[n] = vars[n.expr]
		null
	}

	Void exit(VariableExpr n, Map m) {
		vars[n] = [n]
		null
	}
}
