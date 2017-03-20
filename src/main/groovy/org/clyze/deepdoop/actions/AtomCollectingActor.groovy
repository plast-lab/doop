package org.clyze.deepdoop.actions

import org.clyze.deepdoop.datalog.*
import org.clyze.deepdoop.datalog.clause.*
import org.clyze.deepdoop.datalog.component.*
import org.clyze.deepdoop.datalog.element.*
import org.clyze.deepdoop.datalog.element.atom.*
import org.clyze.deepdoop.datalog.expr.*
import org.clyze.deepdoop.system.*

class AtomCollectingActor implements IActor<IVisitable> {

	Map<IVisitable, Map<String, IAtom>> _declAtoms
	Map<IVisitable, Map<String, IAtom>> _usedAtoms

	AtomCollectingActor() {
		_declAtoms = [:]
		_usedAtoms = [:]
	}

	Map<String, IAtom> getDeclaringAtoms(IVisitable n) {
		def m = _declAtoms[n]
		return (m != null ? m : [:])
	}

	Map<String, IAtom> getUsedAtoms(IVisitable n) {
		def m = _usedAtoms[n]
		return (m != null ? m : [:])
	}


	@Override
	Program exit(Program n, Map<IVisitable, IVisitable> m) {
		Map<String, IAtom> declMap = [:]
		Map<String, IAtom> usedMap = [:]
		declMap.putAll(getDeclaringAtoms(n.globalComp))
		usedMap.putAll(getUsedAtoms(n.globalComp))
		n.comps.values().each{
			declMap.putAll(getDeclaringAtoms(it))
			usedMap.putAll(getUsedAtoms(it))
		}
		_declAtoms[n] = declMap
		_usedAtoms[n] = usedMap
		return n
	}

	@Override
	CmdComponent exit(CmdComponent n, Map<IVisitable, IVisitable> m) {
		Map<String, IAtom> declMap = [:]
		n.declarations.each { declMap.putAll(getDeclaringAtoms(it)) }
		_declAtoms[n] = declMap

		Set<String> importPreds = [] as Set
		n.imports.each{ p ->
			def pName = p.name()
			importPreds.add(pName)
			if (declMap[pName] == null)
				ErrorManager.error(ErrorId.CMD_NO_DECL, pName)
		}
		declMap.keySet().each { declName ->
			if (!importPreds.contains(declName))
				ErrorManager.error(ErrorId.CMD_NO_IMPORT, declName)
		}
		Map<String, IAtom> usedMap = [:]
		n.exports.each{ usedMap[it.name] = it }
		_usedAtoms[n] = usedMap
		return n
	}
	@Override
	Component exit(Component n, Map<IVisitable, IVisitable> m) {
		Map<String, IAtom> declMap = [:]
		// The order of the two loops is important! Rules may contain
		// lang:entity declarations that use a StubAtom instead of the actual
		// Atom. Map.putAll overwrites existing keys and we want to keep the
		// one belonging to the actual declaration.
		n.rules.each{        declMap.putAll(getDeclaringAtoms(it)) }
		n.declarations.each{ declMap.putAll(getDeclaringAtoms(it)) }
		_declAtoms[n] = declMap

		Map<String, IAtom> usedMap = [:]
		n.declarations.each{ usedMap.putAll(getUsedAtoms(it)) }
		n.constraints.each{  usedMap.putAll(getUsedAtoms(it)) }
		n.rules.each{        usedMap.putAll(getUsedAtoms(it)) }
		_usedAtoms[n] = usedMap
		return n
	}

	@Override
	Constraint exit(Constraint n, Map<IVisitable, IVisitable> m) {
		Map<String, IAtom> usedMap = [:]
		usedMap.putAll(getUsedAtoms(n.head))
		usedMap.putAll(getUsedAtoms(n.body))
		_usedAtoms[n] = usedMap
		return n
	}
	@Override
	Declaration exit(Declaration n, Map<IVisitable, IVisitable> m) {
		Map<String, IAtom> declMap = [(n.atom.name()) : n.atom]
		_declAtoms[n] = declMap
		Map<String, IAtom> usedMap = [:]
		n.types.each{ usedMap.putAll(getUsedAtoms(it)) }
		_usedAtoms[n] = usedMap
		return n
	}
	@Override
	RefModeDeclaration exit(RefModeDeclaration n, Map<IVisitable, IVisitable> m) {
		Map<String, IAtom> declMap = [:]
		declMap[n.atom.name()] = n.atom
		declMap[n.types.first().name()] = n.types.first()
		_declAtoms[n] = declMap
		return n
	}
	@Override
	Rule exit(Rule n, Map<IVisitable, IVisitable> m) {
		// Atoms used in the head, are declared in the rule
		Map<String, IAtom> declMap = [:]
		declMap.putAll(getUsedAtoms(n.head))

		Map<String, IAtom> usedMap = [:]
		usedMap.putAll(getUsedAtoms(n.body))

		// Atoms that appear in the head of the rule but have the @past stage
		// are external
		Set<IAtom> toRemove = [] as Set
		declMap.each{ name, atom ->
			if (atom.stage() == "@past")
				toRemove.add(atom)
		}
		toRemove.each{ atom ->
			declMap.remove(atom.name())
			usedMap[atom.name()] = atom
		}
		_declAtoms[n] = declMap
		_usedAtoms[n] = usedMap
		return n
	}


	@Override
	AggregationElement exit(AggregationElement n, Map<IVisitable, IVisitable> m) {
		_usedAtoms[n] = getUsedAtoms(n.body)
		return n
	}
	@Override
	ComparisonElement exit(ComparisonElement n, Map<IVisitable, IVisitable> m) {
		_usedAtoms[n] = getUsedAtoms(n.expr)
		return n
	}
	@Override
	GroupElement exit(GroupElement n, Map<IVisitable, IVisitable> m) {
		_usedAtoms[n] = getUsedAtoms(n.element)
		return n
	}
	@Override
	LogicalElement exit(LogicalElement n, Map<IVisitable, IVisitable> m) {
		Map<String, IAtom> usedMap = [:]
		n.elements.each{ usedMap.putAll(getUsedAtoms(it)) }
		_usedAtoms[n] = usedMap
		return n
	}
	@Override
	NegationElement exit(NegationElement n, Map<IVisitable, IVisitable> m) {
		_usedAtoms[n] = getUsedAtoms(n.element)
		return n
	}

	@Override
	Directive exit(Directive n, Map<IVisitable, IVisitable> m) {
		if (n.isPredicate) {
			Map<String, IAtom> usedMap = [(n.backtick.name()) : n.backtick as IAtom]
			_usedAtoms[n] = usedMap
		}
		return n
	}
	@Override
	Functional exit(Functional n, Map<IVisitable, IVisitable> m) {
		Map<String, IAtom> usedMap = [:]
		usedMap[n.name()] = n
		n.keyExprs.each{ usedMap.putAll(getUsedAtoms(it)) }
		if (n.valueExpr != null) usedMap.putAll(getUsedAtoms(n.valueExpr))
		_usedAtoms[n] = usedMap
		return n
	}
	@Override
	Predicate exit(Predicate n, Map<IVisitable, IVisitable> m) {
		Map<String, IAtom> usedMap = [:]
		usedMap[n.name()] = n
		n.exprs.each{ usedMap.putAll(getUsedAtoms(it)) }
		_usedAtoms[n] = usedMap
		return n
	}
	@Override
	Entity exit(Entity n, Map<IVisitable, IVisitable> m) {
		exit(n as Predicate, m)
		return n
	}
	@Override
	RefMode exit(RefMode n, Map<IVisitable, IVisitable> m) {
		Map<String, IAtom> usedMap = [:]
		usedMap[n.name()] = n
		usedMap.putAll(getUsedAtoms(n.valueExpr))
		_usedAtoms[n] = usedMap
		return n
	}

	@Override
	BinaryExpr exit(BinaryExpr n, Map<IVisitable, IVisitable> m) {
		Map<String, IAtom> usedMap = [:]
		usedMap.putAll(getUsedAtoms(n.left))
		usedMap.putAll(getUsedAtoms(n.right))
		_usedAtoms[n] = usedMap
		return n
	}
	@Override
	FunctionalHeadExpr exit(FunctionalHeadExpr n, Map<IVisitable, IVisitable> m) {
		_usedAtoms[n] = getUsedAtoms(n.functional)
		return n
	}
	@Override
	GroupExpr exit(GroupExpr n, Map<IVisitable, IVisitable> m) {
		_usedAtoms[n] = getUsedAtoms(n.expr)
		return n
	}

	void enter(Program n) {}
	void enter(CmdComponent n) {}
	void enter(Component n) {}

	void enter(Constraint n) {}
	void enter(Declaration n) {}
	void enter(RefModeDeclaration n) {}
	void enter(Rule n) {}

	void enter(AggregationElement n) {}
	void enter(ComparisonElement n) {}
	void enter(GroupElement n) {}
	void enter(LogicalElement n) {}
	void enter(NegationElement n) {}

	void enter(Directive n) {}
	void enter(Functional n) {}
	void enter(Predicate n) {}
	void enter(Entity n) {}
	void enter(Primitive n) {}
	IVisitable exit(Primitive n, Map<IVisitable, IVisitable> m) { return null }
	void enter(RefMode n) {}
	void enter(StubAtom n) {}
	IVisitable exit(StubAtom n, Map<IVisitable, IVisitable> m) { return null }

	void enter(BinaryExpr n) {}
	void enter(ConstantExpr n) {}
	IVisitable exit(ConstantExpr n, Map<IVisitable, IVisitable> m) { return null }
	void enter(FunctionalHeadExpr n) {}
	void enter(GroupExpr n) {}
	void enter(VariableExpr n) {}
	IVisitable exit(VariableExpr n, Map<IVisitable, IVisitable> m) { return null }
}
