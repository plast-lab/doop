package org.clyze.deepdoop.actions

import org.clyze.deepdoop.datalog.*
import org.clyze.deepdoop.datalog.clause.*
import org.clyze.deepdoop.datalog.component.*
import org.clyze.deepdoop.datalog.component.Propagation.Alias
import org.clyze.deepdoop.datalog.element.*
import org.clyze.deepdoop.datalog.element.atom.*
import org.clyze.deepdoop.datalog.expr.*
import org.clyze.deepdoop.system.*

class InitVisitingActor extends PostOrderVisitor<IVisitable> implements IActor<IVisitable>, TDummyActor<IVisitable> {

	// For handling predicate names
	String                   removeName
	String                   initName
	boolean                  inRuleHead
	boolean                  inFrameRules
	Set<String>              declaredAtoms
	// For a given predicate in an (initialized) component get all the
	// components that propagate this predicate.
	Map<String, Set<String>> reverseProps

	Component                curComp
	Map<String, Declaration> autoGenDecls

	AtomCollectingActor      acActor

	InitVisitingActor() {
		// Implemented this way, because Java doesn't allow usage of "this"
		// keyword before all implicit/explicit calls to super/this have
		// returned
		super(null)
		actor = this

		acActor = new AtomCollectingActor()
		autoGenDecls = [:]
	}


	// Need to be overriden because a component might have to be visited
	// multiple times (if initialized multiple times)
	IVisitable visit(Program n) {
		def acVisitor = new PostOrderVisitor<IVisitable>(acActor)
		n.accept(acVisitor)

		// Global Component
		def initP = new Program(n.globalComp.accept(this), [:], [:], [] as Set)

		Map<String, Map<String, Set<String>>> reversePropsMap = [:]
		n.props.each{ prop ->
			def fromTemplateComp = n.comps[n.inits[prop.fromId]]
			def declAtoms        = acActor.getDeclaringAtoms(fromTemplateComp)

			// first collect everything that is not "*" (null orig)
			def toPropagate      = prop.preds.findAll{ it.orig != null }.collect{ it }
			// then if "*" is included, add everything that is not already present
			if (prop.preds.any{ it.orig == null })
				declAtoms.values().each { atom ->
					// not contained in toPropagate
					if (!toPropagate.any{ atom.name == it.orig.name })
						toPropagate.add(new Alias(orig: atom, alias: null))
				}

			initName            = prop.fromId
			inRuleHead          = false
			declaredAtoms       = declAtoms.keySet()

			Set<Alias> newPreds  = [] as Set
			toPropagate.each{ alias ->
				def pred = alias.alias ?: alias.orig

				def reverseMap = reversePropsMap[prop.toId] ?: [:]
				def fromSet    = reverseMap[pred] ?: [] as Set
				fromSet << prop.fromId

				def (newName, newStage) = rename(alias.orig)
				newPreds << new Alias(orig: new Stub(newName), alias: alias.alias)

				reverseMap[pred.name] = fromSet
				reversePropsMap[prop.toId] = reverseMap
			}
			initP.addPropagation(new Propagation(prop.fromId, newPreds, prop.toId))
		}


		// Initializations
		n.inits.each{ initName, compName ->
			def comp = n.comps[compName]

			if (comp == null)
				ErrorManager.error(ErrorId.UNKNOWN_COMP, compName)

			removeName    = null
			this.initName = initName
			inRuleHead    = false
			declaredAtoms = acActor.getDeclaringAtoms(comp).keySet()
			reverseProps  = reversePropsMap[initName]
			curComp       = comp
			initP.addComponent(comp.accept(this) as Component)
		}
		initP.accept(acVisitor)


		Set<String> globalDeclAtoms = acActor.getDeclaringAtoms(n.globalComp).keySet().collect() as Set
		Set<String> globalAtoms     = globalDeclAtoms.collect() as Set
		globalAtoms.addAll(acActor.getUsedAtoms(n.globalComp).keySet())

		inFrameRules = true
		// Propagations
		initP.props.each{ prop ->
			def fromComp = initP.comps[prop.fromId]
			def toComp   = (prop.toId == null ? initP.globalComp : initP.comps[prop.toId])

			if (fromComp == null)
				ErrorManager.error(ErrorId.UNKNOWN_COMP, prop.fromId)
			if (toComp == null)
				ErrorManager.error(ErrorId.UNKNOWN_COMP, prop.toId)

			def declAtoms = acActor.getDeclaringAtoms(fromComp)
			removeName    = prop.fromId
			initName      = prop.toId
			declaredAtoms = declAtoms.keySet()

			prop.preds.each{ alias ->
				def origAtom = declAtoms[alias.orig.name]
				if (origAtom == null)
					ErrorManager.error(ErrorId.UNKNOWN_PRED, alias.orig.name)

				// Ignore lang directives and entities
				if (origAtom instanceof Directive || origAtom instanceof Entity) return

				def atom = alias.alias ?: alias.orig

				// Propagate to global scope
				if (prop.toId == null) {
					def (newName, newStage) = rename(atom)
					// Declared in global space
					if (globalDeclAtoms.contains(newName))
						ErrorManager.error(ErrorId.DEP_GLOBAL, newName)
					// Used in global space (but not declared there)
					// * might be declared inside a component and then propagated to global
					// * might be declared in a different (previous) file
					else if (globalAtoms.contains(newName))
						ErrorManager.warn(ErrorId.DEP_GLOBAL, newName)
				}

				def stage = (prop.toId == null ? null : "@past")
				def vars  = VariableExpr.genTempVars(origAtom.arity)
				def head  = origAtom.newAlias(atom.name, stage, vars).accept(this) as IAtom
				def body  = origAtom.newAtom(null, vars) as IAtom
				toComp.addRule(new Rule(new LogicalElement(head), body, false))
			}
		}
		return initP
	}

	// Need to be overriden to keep track when we are in the head of a rule
	IVisitable visit(Rule n) {
		enter(n)
		Map<IVisitable, IVisitable> m = [:]
		inRuleHead = true
		m[n.head] = n.head.accept(this)
		inRuleHead = false
		if (n.body != null) m[n.body] = n.body.accept(this)
		return exit(n, m)
	}

	CmdComponent exit(CmdComponent n, Map<IVisitable, IVisitable> m) {
		if (!n.rules.isEmpty())
			ErrorManager.error(ErrorId.CMD_RULE)

		Set<Declaration> newDeclarations = [] as Set
		n.declarations.each{ newDeclarations << (m[it] as Declaration) }
		Set<Stub> newImports = [] as Set
		n.imports.each{ newImports << (m[it] as Stub) }
		Set<Stub> newExports = [] as Set
		n.exports.each{ newExports << (m[it] as Stub) }
		return new CmdComponent(initName, newDeclarations, n.eval, newImports, newExports)
	}

	Component exit(Component n, Map<IVisitable, IVisitable> m) {
		Component newComp = new Component(name: initName as String)
		n.declarations.each{ newComp.addDecl(m[it] as Declaration) }
		autoGenDecls.each { newComp.addDecl(it.value) }
		n.constraints.each{ newComp.constraints << (m[it] as Constraint) }
		n.rules.each{ newComp.rules << (m[it] as Rule) }
		return newComp
	}

	Constraint exit(Constraint n, Map<IVisitable, IVisitable> m) {
		new Constraint(m[n.head] as IElement, m[n.body] as IElement)
	}

	Declaration exit(Declaration n, Map<IVisitable, IVisitable> m) {
		new Declaration(m[n.atom] as IAtom, n.types.collect{ m[it] as IAtom })
	}

	RefModeDeclaration exit(RefModeDeclaration n, Map<IVisitable, IVisitable> m) {
		new RefModeDeclaration(m[n.atom] as RefMode, m[n.types[0]] as Predicate, m[n.types[1]] as Primitive)
	}

	Rule exit(Rule n, Map<IVisitable, IVisitable> m) {
		new Rule(m[n.head], m[n.body], false)
	}

	AggregationElement exit(AggregationElement n, Map<IVisitable, IVisitable> m) {
		new AggregationElement(m[n.var] as VariableExpr, m[n.predicate] as Predicate, m[n.body] as IElement)
	}

	ComparisonElement exit(ComparisonElement n, Map<IVisitable, IVisitable> m) {
		new ComparisonElement(m[n.expr] as BinaryExpr)
	}

	GroupElement exit(GroupElement n, Map<IVisitable, IVisitable> m) {
		new GroupElement(m[n.element] as IElement)
	}

	LogicalElement exit(LogicalElement n, Map<IVisitable, IVisitable> m) {
		def newElements = []
		n.elements.each{ newElements << (m[it] as IElement) }
 		return new LogicalElement(n.type, newElements)
	}

	NegationElement exit(NegationElement n, Map<IVisitable, IVisitable> m) {
		new NegationElement(m[n.element] as IElement)
	}

	Constructor exit(Constructor n, Map<IVisitable, IVisitable> m) {
		def (newName, newStage) = rename(n)
		def newKeyExprs = n.keyExprs.collect{ m[it] as IExpr }

		if (!inFrameRules && n.stage == "@past" && n.name in declaredAtoms && !autoGenDecls[newName]) {
			def decl = curComp.declarations.find{ it.atom.name == n.name }
			if (!decl)
				ErrorManager.error(ErrorId.NO_DECL_REC, n.name)
			def newVars = decl.types.collect{ it.getVars().first() }
			def newValueVar = newVars.takeRight(1)
			def newKeyVars = newVars.dropRight(1)
			autoGenDecls[newName] = new Declaration(new Functional(newName, null, newKeyVars, newValueVar), decl.types)
		}
		def newFunctional = new Functional(newName, newStage, newKeyExprs, m[n.valueExpr] as IExpr)
		return new Constructor(newFunctional, n.entity)
	}

	Directive exit(Directive n, Map<IVisitable, IVisitable> m) {
		if (n.isPredicate)
			return new Directive(n.name, m[n.backtick] as Stub)
		else
			return new Directive(n.name, m[n.backtick] as Stub, m[n.constant] as ConstantExpr)
	}

	Entity exit(Entity n, Map<IVisitable, IVisitable> m) {
		def (newName, newStage) = rename(n)
		def newExpr = m[n.exprs.first()] as IExpr
		return new Entity(newName, newStage, newExpr)
	}

	Functional exit(Functional n, Map<IVisitable, IVisitable> m) {
		def (newName, newStage) = rename(n)
		def newKeyExprs = n.keyExprs.collect{ m[it] as IExpr }

		if (!inFrameRules && n.stage == "@past" && n.name in declaredAtoms && !autoGenDecls[newName]) {
			def decl = curComp.declarations.find{ it.atom.name == n.name }
			if (!decl)
				ErrorManager.error(ErrorId.NO_DECL_REC, n.name)
			def newVars = decl.types.collect{ it.getVars().first() }
			def newValueVar = newVars.takeRight(1)
			def newKeyVars = newVars.dropRight(1)
			autoGenDecls[newName] = new Declaration(new Functional(newName, null, newKeyVars, newValueVar), decl.types)
		}
		return new Functional(newName, newStage, newKeyExprs, m[n.valueExpr] as IExpr)
	}

	Predicate exit(Predicate n, Map<IVisitable, IVisitable> m) {
		def (newName, newStage) = rename(n)
		def newExprs = n.exprs.collect{ m[it] as IExpr }

		if (!inFrameRules && n.stage == "@past" && n.name in declaredAtoms && !autoGenDecls[newName]) {
			def decl = curComp.declarations.find{ it.atom.name == n.name }
			if (!decl)
				ErrorManager.error(ErrorId.NO_DECL_REC, n.name)
			def newVars = decl.types.collect{ it.getVars().first() }
			autoGenDecls[newName] = new Declaration(new Predicate(newName, null, newVars), decl.types)
		}
		return new Predicate(newName, newStage, newExprs)
	}

	Primitive exit(Primitive n, Map<IVisitable, IVisitable> m) { n }

	RefMode exit(RefMode n, Map<IVisitable, IVisitable> m) {
		def (newName, newStage) = rename(n)
		return new RefMode(newName, newStage, m[n.entityVar] as VariableExpr, m[n.valueExpr] as IExpr)
	}

	Stub exit(Stub n, Map<IVisitable, IVisitable> m) {
		def (newName, newStage) = rename(n)
		return new Stub(newName)
	}

	BinaryExpr exit(BinaryExpr n, Map<IVisitable, IVisitable> m) {
		new BinaryExpr(m[n.left] as IExpr, n.op, m[n.right] as IExpr)
	}

	ConstantExpr exit(ConstantExpr n, Map<IVisitable, IVisitable> m) { n }

	FunctionalHeadExpr exit(FunctionalHeadExpr n, Map<IVisitable, IVisitable> m) {
		new FunctionalHeadExpr(m[n.functional] as Functional)
	}

	GroupExpr exit(GroupExpr n, Map<IVisitable, IVisitable> m) {
		new GroupExpr(m[n.expr] as IExpr)
	}

	VariableExpr exit(VariableExpr n, Map<IVisitable, IVisitable> m) { n }


	def rename(IAtom atom) {
		def name = atom.name

		if (removeName != null && name.startsWith(removeName + ":"))
			name = name.replaceFirst(removeName + ":", "")

		Set<String> reverseSet = null
		if (reverseProps != null)
			reverseSet = reverseProps[atom.name]
		assert (reverseSet == null || reverseSet.size() == 1)

		// NOTE: This if should go before the next one, since the heuristic for
		// discovering predicated declared in a component will assume that a
		// @past predicate in the head of the rule is declared in the
		// component.
		if (atom.stage == "@past") {
			// * we are in the global component, thus in a custom frame rule
			if (initName == null)
				return new Tuple2(name + ":past", "@past")
			// * if @past is used in the head of a rule
			// * if @past is used for an entity
			// then fix name accordingly
			else if (inRuleHead || atom instanceof Entity) {
				if (reverseSet == null)
					return new Tuple2(name, null)
				else
					return new Tuple2(reverseSet.first() + ":" + name, null)
			}
			// * else explicitly add the appropriate prefix and suffix
			else return new Tuple2(initName + ":" + name + ":past", "@past")
		}

		// * if the atom is declared in this component, add the appropriate prefix
		if (declaredAtoms != null && name in declaredAtoms)
			return new Tuple2(initName + ":" + name, atom.stage)

		// * if the atom is propagated from another component, explicitly add
		// the appropriate prefix and suffix
		if (reverseSet != null)
			return new Tuple2(initName + ":" + name + ":past", "@past")

		// * otherwise it is an external atom, thus leave the name unaltered
		return new Tuple2(name, atom.stage)
	}
}
