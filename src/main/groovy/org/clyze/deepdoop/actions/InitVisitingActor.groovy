package org.clyze.deepdoop.actions

import jas.Var
import jdk.nashorn.api.scripting.ScriptUtils
import org.clyze.deepdoop.datalog.*
import org.clyze.deepdoop.datalog.clause.*
import org.clyze.deepdoop.datalog.component.*
import org.clyze.deepdoop.datalog.element.*
import org.clyze.deepdoop.datalog.element.atom.*
import org.clyze.deepdoop.datalog.expr.*
import org.clyze.deepdoop.system.*

class InitVisitingActor extends PostOrderVisitor<IVisitable> implements IActor<IVisitable> {

	// For handling predicate names
	String                   _removeName
	String                   _initName
	boolean                  _inRuleHead
	Set<String>              _declaredAtoms
	// For a given predicate in an (initialized) component get all the
	// components that propagate this predicate.
	Map<String, Set<String>> _reverseProps

	AtomCollectingActor      _acActor

	InitVisitingActor() {
		// Implemented this way, because Java doesn't allow usage of "this"
		// keyword before all implicit/explicit calls to super/this have
		// returned
		super(null)
		_actor = this

		_acActor = new AtomCollectingActor()
	}


	// Need to be overriden because a component might have to be visited
	// multiple times (if initialized multiple times)
	@Override
	IVisitable visit(Program n) {
		PostOrderVisitor<IVisitable> acVisitor = new PostOrderVisitor<>(_acActor)
		n.accept(acVisitor)

		// Global Component
		def newGlobal = n.globalComp.accept(this) as Component
		def initP = Program.from(newGlobal, [:], [:], [] as Set)


		Map<String, Map<String, Set<String>>> reversePropsMap = [:]
		n.props.each{ prop ->
			def fromTemplateComp = n.comps[n.inits[prop.fromId]]
			def declAtoms        = _acActor.getDeclaringAtoms(fromTemplateComp)
			// empty means "*" => propagate everything
			def toPropagate      = ( prop.preds.isEmpty() ? declAtoms.values() : prop.preds )
			Set<IAtom> newPreds  = [] as Set

			_initName            = prop.fromId
			_inRuleHead          = false
			_declaredAtoms       = declAtoms.keySet()

			toPropagate.each{ pred ->
				def reverseMap = reversePropsMap[prop.toId]
				if (reverseMap == null) reverseMap = [:]
				Set<String> fromSet = reverseMap[pred]
				if (fromSet == null) fromSet = [] as Set
				fromSet.add(prop.fromId)
				def (String newName, String newStage) = rename(pred)
				newPreds.add(new StubAtom(newName))

				reverseMap[pred.name()] = fromSet
				reversePropsMap[prop.toId] = reverseMap
			}
			initP.addPropagation(prop.fromId, newPreds, prop.toId)
		}


		// Initializations
		n.inits.each{ initName, compName ->
			def comp = n.comps[compName]

			if (comp == null)
				ErrorManager.error(ErrorId.UNKNOWN_COMP, compName)

			_removeName    = null
			_initName      = initName
			_inRuleHead    = false
			_declaredAtoms = _acActor.getDeclaringAtoms(comp).keySet()
			_reverseProps  = reversePropsMap[initName]
			initP.addComponent(comp.accept(this) as Component)
		}
		initP.accept(acVisitor)


		Set<String> globalDeclAtoms = _acActor.getDeclaringAtoms(n.globalComp).keySet().collect() as Set
		Set<String> globalAtoms     = globalDeclAtoms.collect() as Set
		globalAtoms.addAll(_acActor.getUsedAtoms(n.globalComp).keySet())

		// Propagations
		initP.props.each{ prop ->
			def fromComp = initP.comps[prop.fromId]
			def toComp   = (prop.toId == null ? initP.globalComp : initP.comps[prop.toId])

			if (fromComp == null)
				ErrorManager.error(ErrorId.UNKNOWN_COMP, prop.fromId)
			if (toComp == null)
				ErrorManager.error(ErrorId.UNKNOWN_COMP, prop.toId)

			def declAtoms = _acActor.getDeclaringAtoms(fromComp)
			_removeName    = prop.fromId
			_initName      = prop.toId
			_declaredAtoms = declAtoms.keySet()

			prop.preds.each{ stubAtom ->
				def atom = declAtoms.get(stubAtom.name())
				if (atom == null)
					ErrorManager.error(ErrorId.UNKNOWN_PRED, stubAtom.name())

				// Ignore lang directives and entities
				if (atom instanceof Directive || atom instanceof Entity) return

				// Propagate to global scope
				if (prop.toId == null) {
					def (String newName, String newStage) = rename(atom)
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
				def vars  = VariableExpr.genTempVars(atom.arity())
				def head  = atom.instantiate(stage, vars).accept(this) as IAtom
				def body  = atom.instantiate(null, vars) as IAtom
				toComp.addRule(new Rule(new LogicalElement(head), body, false))
			}
		}
		return initP
	}

	// Need to be overriden to keep track when we are in the head of a rule
	@Override
	IVisitable visit(Rule n) {
		enter(n)
		Map<IVisitable, IVisitable> m = [:]
		_inRuleHead = true
		m[n.head] = n.head.accept(this)
		_inRuleHead = false
		if (n.body != null) m[n.body] = n.body.accept(this)
		return exit(n, m)
	}


	@Override
	CmdComponent exit(CmdComponent n, Map<IVisitable, IVisitable> m) {
		if (!n.rules.isEmpty())
			ErrorManager.error(ErrorId.CMD_RULE)

		Set<Declaration> newDeclarations = [] as Set
		n.declarations.each{ newDeclarations.add(m[it] as Declaration) }
		Set<StubAtom> newImports = [] as Set
		n.imports.each{ newImports.add(m[it] as StubAtom) }
		Set<StubAtom> newExports = [] as Set
		n.exports.each{ newExports.add(m[it] as StubAtom) }
		return new CmdComponent(_initName, newDeclarations, n.eval, newImports, newExports)
	}
	@Override
	Component exit(Component n, Map<IVisitable, IVisitable> m) {
		Component newComp = new Component(_initName as String)
		n.declarations.each{ newComp.declarations.add(m[it] as Declaration) }
		n.constraints.each{ newComp.constraints.add(m[it] as Constraint) }
		n.rules.each{ newComp.rules.add(m[it] as Rule) }
		return newComp
	}

	@Override
	Constraint exit(Constraint n, Map<IVisitable, IVisitable> m) {
		return new Constraint(m[n.head] as IElement, m[n.body] as IElement)
	}
	@Override
	Declaration exit(Declaration n, Map<IVisitable, IVisitable> m) {
		Set<IAtom> newTypes = [] as Set
		n.types.each{ newTypes.add(m[it] as IAtom) }
		return new Declaration(m[n.atom] as IAtom, newTypes)
	}
	@Override
	RefModeDeclaration exit(RefModeDeclaration n, Map<IVisitable, IVisitable> m) {
		return new RefModeDeclaration(m[n.atom] as RefMode, m[n.types.get(0)] as Predicate, m[n.types.get(1)] as Primitive)
	}
	@Override
	Rule exit(Rule n, Map<IVisitable, IVisitable> m) {
		return new Rule(m[n.head] as LogicalElement, m[n.body] as IElement, false)
	}

	@Override
	AggregationElement exit(AggregationElement n, Map<IVisitable, IVisitable> m) {
		return new AggregationElement(m[n.var] as VariableExpr, m[n.predicate] as Predicate, m[n.body] as IElement)
	}
	@Override
	ComparisonElement exit(ComparisonElement n, Map<IVisitable, IVisitable> m) {
		return new ComparisonElement(m[n.expr] as BinaryExpr)
	}
	@Override
	GroupElement exit(GroupElement n, Map<IVisitable, IVisitable> m) {
		return new GroupElement(m[n.element] as IElement)
	}
	@Override
	LogicalElement exit(LogicalElement n, Map<IVisitable, IVisitable> m) {
		Set<IElement> newElements = [] as Set
		n.elements.each{ newElements.add(m[it] as IElement) }
		return new LogicalElement(n.type, newElements)
	}
	@Override
	NegationElement exit(NegationElement n, Map<IVisitable, IVisitable> m) {
		return new NegationElement(m[n.element] as IElement)
	}

	@Override
	Directive exit(Directive n, Map<IVisitable, IVisitable> m) {
		if (n.isPredicate)
			return new Directive(n.name, m[n.backtick] as StubAtom)
		else
			return new Directive(n.name, m[n.backtick] as StubAtom, m[n.constant] as ConstantExpr)
	}
	@Override
	Functional exit(Functional n, Map<IVisitable, IVisitable> m) {
		List<IExpr> newKeyExprs = []
		n.keyExprs.each{ newKeyExprs.add(m[it] as IExpr) }
		def (String newName, String newStage) = rename(n)
		return new Functional(newName, newStage, newKeyExprs, m[n.valueExpr] as IExpr)
	}
	@Override
	Predicate exit(Predicate n, Map<IVisitable, IVisitable> m) {
		List<IExpr> newExprs = []
		n.exprs.each{ newExprs.add(m[it] as IExpr) }
		def (String newName, String newStage) = rename(n)
		return new Predicate(newName, newStage, newExprs)
	}
	@Override
	Entity exit(Entity n, Map<IVisitable, IVisitable> m) {
		List<IExpr> newExprs = []
		n.exprs.each{ newExprs.add(m[it] as IExpr) }
		def (String newName, String newStage) = rename(n)
		return new Entity(newName, newStage, newExprs)
	}
	@Override
	Primitive exit(Primitive n, Map<IVisitable, IVisitable> m) {
		return n
	}
	@Override
	RefMode exit(RefMode n, Map<IVisitable, IVisitable> m) {
		def (String newName, String newStage) = rename(n)
		return new RefMode(newName, newStage, m[n.entityVar] as VariableExpr, m[n.valueExpr] as IExpr)
	}
	@Override
	StubAtom exit(StubAtom n, Map<IVisitable, IVisitable> m) {
		def (String newName, String newStage) = rename(n)
		return new StubAtom(newName)
	}


	@Override
	BinaryExpr exit(BinaryExpr n, Map<IVisitable, IVisitable> m) {
		return new BinaryExpr(m[n.left] as IExpr, n.op, m[n.right] as IExpr)
	}
	@Override
	ConstantExpr exit(ConstantExpr n, Map<IVisitable, IVisitable> m) {
		return n
	}
	@Override
	FunctionalHeadExpr exit(FunctionalHeadExpr n, Map<IVisitable, IVisitable> m) {
		return new FunctionalHeadExpr(m[n.functional] as Functional)
	}
	@Override
	GroupExpr exit(GroupExpr n, Map<IVisitable, IVisitable> m) {
		return new GroupExpr(m[n.expr] as IExpr)
	}
	@Override
	VariableExpr exit(VariableExpr n, Map<IVisitable, IVisitable> m) {
		return n
	}


	def rename(IAtom atom) {
		def name = atom.name()

		if (_removeName != null && name.startsWith(_removeName + ":"))
			name = name.replaceFirst(_removeName + ":", "")

		Set<String> reverseSet = null
		if (_reverseProps != null)
			reverseSet = _reverseProps.get(atom.name())
		assert (reverseSet == null || reverseSet.size() == 1)

		// NOTE: This if should go before the next one, since the heuristic for
		// discovering predicated declared in a component will assume that a
		// @past predicate in the head of the rule is declared in the
		// component.
		if (atom.stage() == "@past") {
			// * we are in the global component, thus in a custom frame rule
			if (_initName == null)
				return new Tuple2(name + ":past", "@past")
			// * if @past is used in the head of a rule
			// * if @past is used for an entity
			// then fix name accordingly
			else if (_inRuleHead || atom instanceof Entity) {
				if (reverseSet == null)
					return new Tuple2(name, null)
				else
					return new Tuple2(reverseSet.first() + ":" + name, null)
			}
			// * else explicitly add the appropriate prefix and suffix
			else return new Tuple2(_initName + ":" + name + ":past", "@past")
		}

		// * if the atom is declared in this component, add the appropriate prefix
		if (_declaredAtoms != null && _declaredAtoms.contains(name))
			return new Tuple2(_initName + ":" + name, atom.stage())

		// * if the atom is propagated from another component, explicitly add
		// the appropriate prefix and suffix
		if (reverseSet != null)
			return new Tuple2(_initName + ":" + name + ":past", "@past")

		// * otherwise it is an external atom, thus leave the name unaltered
		return new Tuple2(name, atom.stage())
	}

	void enter(Program n) {}
	IVisitable exit(Program n, Map<IVisitable, IVisitable> m) { return null }

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
	void enter(RefMode n) {}
	void enter(StubAtom n) {}

	void enter(BinaryExpr n) {}
	void enter(ConstantExpr n) {}
	void enter(FunctionalHeadExpr n) {}
	void enter(GroupExpr n) {}
	void enter(VariableExpr n) {}
}
