package org.clyze.deepdoop.actions

import org.clyze.deepdoop.datalog.*
import org.clyze.deepdoop.datalog.clause.*
import org.clyze.deepdoop.datalog.component.*
import org.clyze.deepdoop.datalog.element.*
import org.clyze.deepdoop.datalog.element.atom.*
import org.clyze.deepdoop.datalog.expr.*
import org.clyze.deepdoop.system.*

class AtomCollectingActor implements IActor<IVisitable>, TDummyActor<IVisitable> {

	Map<IVisitable, Map<String, IAtom>> declAtoms = [:]
	Map<IVisitable, Map<String, IAtom>> usedAtoms = [:]

	Map<String, IAtom> getDeclaringAtoms(IVisitable n) {
		declAtoms[n] ?: [:]
	}
	Map<String, IAtom> getUsedAtoms(IVisitable n) {
		usedAtoms[n] ?: [:]
	}

	Program exit(Program n, Map<IVisitable, IVisitable> m) {
		Map<String, IAtom> declMap = [:] << getDeclaringAtoms(n.globalComp)
		Map<String, IAtom> usedMap = [:] << getUsedAtoms(n.globalComp)
		n.comps.values().each{
			declMap << getDeclaringAtoms(it)
			usedMap << getUsedAtoms(it)
		}
		declAtoms[n] = declMap
		usedAtoms[n] = usedMap
		return n
	}

	CmdComponent exit(CmdComponent n, Map<IVisitable, IVisitable> m) {
		Map<String, IAtom> declMap = [:]
		n.declarations.each { declMap << getDeclaringAtoms(it) }
		declAtoms[n] = declMap

		Set<String> importPreds = [] as Set
		n.imports.each{ p ->
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
		n.exports.each{ usedMap[it.name] = it }
		usedAtoms[n] = usedMap
		return n
	}

	Component exit(Component n, Map<IVisitable, IVisitable> m) {
		Map<String, IAtom> declMap = [:]
		// The order of the two loops is important! Rules may contain
		// lang:entity declarations that use a Stub instead of the actual
		// Atom. Map.putAll overwrites existing keys and we want to keep the
		// one belonging to the actual declaration.
		n.rules.each{        declMap << getDeclaringAtoms(it) }
		n.declarations.each{ declMap << getDeclaringAtoms(it) }
		declAtoms[n] = declMap

		Map<String, IAtom> usedMap = [:]
		n.declarations.each{ usedMap << getUsedAtoms(it) }
		n.constraints.each{  usedMap << getUsedAtoms(it) }
		n.rules.each{        usedMap << getUsedAtoms(it) }
		usedAtoms[n] = usedMap
		return n
	}

	Constraint exit(Constraint n, Map<IVisitable, IVisitable> m) {
		Map<String, IAtom> usedMap = [:]
		usedMap << getUsedAtoms(n.head)
		usedMap << getUsedAtoms(n.body)
		usedAtoms[n] = usedMap
		return n
	}

	Declaration exit(Declaration n, Map<IVisitable, IVisitable> m) {
		Map<String, IAtom> declMap = [(n.atom.name) : n.atom]
		declAtoms[n] = declMap
		Map<String, IAtom> usedMap = [:]
		n.types.each{ usedMap << getUsedAtoms(it) }
		usedAtoms[n] = usedMap
		return n
	}

	RefModeDeclaration exit(RefModeDeclaration n, Map<IVisitable, IVisitable> m) {
		Map<String, IAtom> declMap = [:]
		declMap[n.atom.name] = n.atom
		declMap[n.types.first().name] = n.types.first()
		declAtoms[n] = declMap
		return n
	}

	Rule exit(Rule n, Map<IVisitable, IVisitable> m) {
		// Atoms used in the head, are declared in the rule
		Map<String, IAtom> declMap = [:] << getUsedAtoms(n.head)
		Map<String, IAtom> usedMap = [:] << getUsedAtoms(n.body)

		// Atoms that appear in the head of the rule but have the @past stage
		// are external
		declMap.findAll{ name, atom -> atom.stage == "@past" }
				.each{ name, atom ->
					declMap.remove(name)
					usedMap[name] = atom
				}
		declAtoms[n] = declMap
		usedAtoms[n] = usedMap
		return n
	}

	AggregationElement exit(AggregationElement n, Map<IVisitable, IVisitable> m) {
		usedAtoms[n] = getUsedAtoms(n.body)
		return n
	}

	ComparisonElement exit(ComparisonElement n, Map<IVisitable, IVisitable> m) {
		usedAtoms[n] = getUsedAtoms(n.expr)
		return n
	}

	GroupElement exit(GroupElement n, Map<IVisitable, IVisitable> m) {
		usedAtoms[n] = getUsedAtoms(n.element)
		return n
	}

	LogicalElement exit(LogicalElement n, Map<IVisitable, IVisitable> m) {
		Map<String, IAtom> usedMap = [:]
		n.elements.each{ usedMap << getUsedAtoms(it) }
		usedAtoms[n] = usedMap
		return n
	}

	NegationElement exit(NegationElement n, Map<IVisitable, IVisitable> m) {
		usedAtoms[n] = getUsedAtoms(n.element)
		return n
	}

	Constructor exit(Constructor n, Map<IVisitable, IVisitable> m) {
		exit(n as Functional, m)
		return n
	}

	Directive exit(Directive n, Map<IVisitable, IVisitable> m) {
		if (n.isPredicate) {
			Map<String, IAtom> usedMap = [(n.backtick.name) : n.backtick as IAtom]
			usedAtoms[n] = usedMap
		}
		return n
	}

	Entity exit(Entity n, Map<IVisitable, IVisitable> m) {
		exit(n as Predicate, m)
		return n
	}

	Functional exit(Functional n, Map<IVisitable, IVisitable> m) {
		Map<String, IAtom> usedMap = [:]
		usedMap[n.name] = n
		n.keyExprs.each{ usedMap << getUsedAtoms(it) }
		if (n.valueExpr) usedMap << getUsedAtoms(n.valueExpr)
		usedAtoms[n] = usedMap
		return n
	}

	Predicate exit(Predicate n, Map<IVisitable, IVisitable> m) {
		Map<String, IAtom> usedMap = [:]
		usedMap[n.name] = n
		n.exprs.each{ usedMap << getUsedAtoms(it) }
		usedAtoms[n] = usedMap
		return n
	}

	RefMode exit(RefMode n, Map<IVisitable, IVisitable> m) {
		Map<String, IAtom> usedMap = [:]
		usedMap[n.name] = n
		usedMap << getUsedAtoms(n.valueExpr)
		usedAtoms[n] = usedMap
		return n
	}

	BinaryExpr exit(BinaryExpr n, Map<IVisitable, IVisitable> m) {
		Map<String, IAtom> usedMap = [:]
		usedMap << getUsedAtoms(n.left)
		usedMap << getUsedAtoms(n.right)
		usedAtoms[n] = usedMap
		return n
	}

	FunctionalHeadExpr exit(FunctionalHeadExpr n, Map<IVisitable, IVisitable> m) {
		usedAtoms[n] = getUsedAtoms(n.functional)
		return n
	}

	GroupExpr exit(GroupExpr n, Map<IVisitable, IVisitable> m) {
		usedAtoms[n] = getUsedAtoms(n.expr)
		return n
	}
}
