package org.clyze.deepdoop.actions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import org.clyze.deepdoop.datalog.*;
import org.clyze.deepdoop.datalog.clause.*;
import org.clyze.deepdoop.datalog.component.*;
import org.clyze.deepdoop.datalog.element.*;
import org.clyze.deepdoop.datalog.element.atom.*;
import org.clyze.deepdoop.datalog.expr.*;
import org.clyze.deepdoop.system.*;

public class InitVisitingActor extends PostOrderVisitor<IVisitable> implements IActor<IVisitable> {

	// For handling predicate names
	String                   _removeName;
	String                   _initName;
	boolean                  _inRuleHead;
	Set<String>              _declaredAtoms;
	// For a given predicate in an (initialized) component get all the
	// components that propagate this predicate.
	Map<IAtom, Set<String>>  _reverseProps;

	AtomCollectingActor      _acActor;

	public InitVisitingActor() {
		// Implemented this way, because Java doesn't allow usage of "this"
		// keyword before all implicit/explicit calls to super/this have
		// returned
		super(null);
		_actor = this;

		_acActor = new AtomCollectingActor();
	}


	// Need to be overriden because a component might have to be visited
	// multiple times (if initialized multiple times)
	@Override
	public IVisitable visit(Program n) {
		PostOrderVisitor<IVisitable> acVisitor = new PostOrderVisitor<>(_acActor);
		n.accept(acVisitor);

		// Global Component
		Component newGlobal = (Component) n.globalComp.accept(this);
		Program initP = Program.from(newGlobal, new HashMap<>(), new HashMap<>(), new HashSet<>());


		Map<String, Map<IAtom, Set<String>>> reversePropsMap = new HashMap<>();
		n.props.forEach( prop -> {
			Component fromTemplateComp    = n.comps.get(n.inits.get(prop.fromId));
			Map<String, IAtom> declAtoms  = _acActor.getDeclaringAtoms(fromTemplateComp);
			// empty means "*" => propagate everything
			Collection<IAtom> toPropagate = ( prop.preds.isEmpty() ? declAtoms.values() : prop.preds );
			Set<IAtom> newPreds           = new HashSet<>();

			_initName                     = prop.fromId;
			_inRuleHead                   = false;
			_declaredAtoms                = declAtoms.keySet();

			toPropagate.forEach( pred -> {
				Map<IAtom, Set<String>> reverseMap = reversePropsMap.get(prop.toId);
				if (reverseMap == null) reverseMap = new HashMap<>();
				Set<String> fromSet = reverseMap.get(pred);
				if (fromSet == null) fromSet = new HashSet<>();
				fromSet.add(prop.fromId);
				Pair p = rename(pred);
				newPreds.add(new StubAtom(p.name));

				reverseMap.put(pred, fromSet);
				reversePropsMap.put(prop.toId, reverseMap);
			});
			initP.addPropagation(prop.fromId, newPreds, prop.toId);
		});


		// Initializations
		n.inits.forEach( (initName, compName) -> {
			Component comp = n.comps.get(compName);

			if (comp == null)
				ErrorManager.error(ErrorId.UNKNOWN_COMP, compName);

			_removeName    = null;
			_initName      = initName;
			_inRuleHead    = false;
			_declaredAtoms = _acActor.getDeclaringAtoms(comp).keySet();
			_reverseProps  = reversePropsMap.get(initName);
			initP.addComponent((Component) comp.accept(this));
		});
		initP.accept(acVisitor);


		Set<String> globalDeclAtoms = new HashSet<>(_acActor.getDeclaringAtoms(n.globalComp).keySet());
		Set<String> globalAtoms     = new HashSet<>(globalDeclAtoms);
		globalAtoms.addAll(_acActor.getUsedAtoms(n.globalComp).keySet());

		// Propagations
		initP.props.forEach( prop -> {
			Component fromComp = initP.comps.get(prop.fromId);
			Component toComp   = (prop.toId == null ? initP.globalComp : initP.comps.get(prop.toId));

			if (fromComp == null)
				ErrorManager.error(ErrorId.UNKNOWN_COMP, prop.fromId);
			if (toComp == null)
				ErrorManager.error(ErrorId.UNKNOWN_COMP, prop.toId);

			Map<String, IAtom> declAtoms = _acActor.getDeclaringAtoms(fromComp);
			_removeName        = prop.fromId;
			_initName          = prop.toId;
			_declaredAtoms     = declAtoms.keySet();

			prop.preds.forEach( stubAtom -> {
				IAtom atom = declAtoms.get(stubAtom.name());
				if (atom == null)
					ErrorManager.error(ErrorId.UNKNOWN_PRED, stubAtom.name());

				// Ignore lang directives and entities
				if (atom instanceof Directive || atom instanceof Entity) return;

				// Propagate to global scope
				if (prop.toId == null) {
					Pair p = rename(atom);
					String newName = p.name;
					// Declared in global space
					if (globalDeclAtoms.contains(newName))
						ErrorManager.error(ErrorId.DEP_GLOBAL, newName);
					// Used in global space (but not declared there)
					// * might be declared inside a component and then propagated to global
					// * might be declared in a different (previous) file
					else if (globalAtoms.contains(newName))
						ErrorManager.warn(ErrorId.DEP_GLOBAL, newName);
				}

				String stage            = (prop.toId == null ? null : "@past");
				List<VariableExpr> vars = VariableExpr.genTempVars(atom.arity());
				IElement head           = (IAtom) atom.instantiate(stage, vars).accept(this);
				IElement body           = (IAtom) atom.instantiate(null, vars);
				toComp.addRule(new Rule(new LogicalElement(head), body, false));
			});
		});
		return initP;
	}

	// Need to be overriden to keep track when we are in the head of a rule
	@Override
	public IVisitable visit(Rule n) {
		enter(n);
		Map<IVisitable, IVisitable> m = new HashMap<>();
		_inRuleHead = true;
		m.put(n.head, n.head.accept(this));
		_inRuleHead = false;
		if (n.body != null) m.put(n.body, n.body.accept(this));
		return exit(n, m);
	}


	@Override
	public CmdComponent exit(CmdComponent n, Map<IVisitable, IVisitable> m) {
		if (!n.rules.isEmpty())
			ErrorManager.error(ErrorId.CMD_RULE);

		Set<Declaration> newDeclarations = new HashSet<>();
		for (Declaration d : n.declarations) newDeclarations.add((Declaration) m.get(d));
		Set<StubAtom> newImports = new HashSet<>();
		for (StubAtom p : n.imports) newImports.add((StubAtom) m.get(p));
		Set<StubAtom> newExports = new HashSet<>();
		for (StubAtom p : n.exports) newExports.add((StubAtom) m.get(p));
		return new CmdComponent(_initName, newDeclarations, n.eval, newImports, newExports);
	}
	@Override
	public Component exit(Component n, Map<IVisitable, IVisitable> m) {
		Component newComp = new Component(_initName);
		for (Declaration d : n.declarations) newComp.declarations.add((Declaration) m.get(d));
		for (Constraint c : n.constraints)   newComp.constraints.add((Constraint) m.get(c));
		for (Rule r : n.rules)               newComp.rules.add((Rule) m.get(r));
		return newComp;
	}

	@Override
	public Constraint exit(Constraint n, Map<IVisitable, IVisitable> m) {
		return new Constraint((IElement) m.get(n.head), (IElement) m.get(n.body));
	}
	@Override
	public Declaration exit(Declaration n, Map<IVisitable, IVisitable> m) {
		Set<IAtom> newTypes = new HashSet<>();
		for (IAtom t : n.types) newTypes.add((IAtom) m.get(t));
		return new Declaration((IAtom) m.get(n.atom), newTypes);
	}
	@Override
	public RefModeDeclaration exit(RefModeDeclaration n, Map<IVisitable, IVisitable> m) {
		return new RefModeDeclaration((RefMode) m.get(n.atom), (Predicate) m.get(n.types.get(0)), (Primitive) m.get(n.types.get(1)));
	}
	@Override
	public Rule exit(Rule n, Map<IVisitable, IVisitable> m) {
		return new Rule((LogicalElement) m.get(n.head), (IElement) m.get(n.body), false);
	}

	@Override
	public AggregationElement exit(AggregationElement n, Map<IVisitable, IVisitable> m) {
		return new AggregationElement((VariableExpr) m.get(n.var), (Predicate) m.get(n.predicate), (IElement) m.get(n.body));
	}
	@Override
	public ComparisonElement exit(ComparisonElement n, Map<IVisitable, IVisitable> m) {
		return new ComparisonElement((BinaryExpr) m.get(n.expr));
	}
	@Override
	public GroupElement exit(GroupElement n, Map<IVisitable, IVisitable> m) {
		return new GroupElement((IElement) m.get(n.element));
	}
	@Override
	public LogicalElement exit(LogicalElement n, Map<IVisitable, IVisitable> m) {
		Set<IElement> newElements = new HashSet<>();
		for (IElement e : n.elements) newElements.add((IElement) m.get(e));
		return new LogicalElement(n.type, newElements);
	}
	@Override
	public NegationElement exit(NegationElement n, Map<IVisitable, IVisitable> m) {
		return new NegationElement((IElement) m.get(n.element));
	}

	@Override
	public Directive exit(Directive n, Map<IVisitable, IVisitable> m) {
		if (n.isPredicate)
			return new Directive(n.name, (StubAtom) m.get(n.backtick));
		else
			return new Directive(n.name, (StubAtom) m.get(n.backtick), (ConstantExpr) m.get(n.constant));
	}
	@Override
	public Functional exit(Functional n, Map<IVisitable, IVisitable> m) {
		List<IExpr> newKeyExprs = new ArrayList<>();
		for (IExpr e : n.keyExprs) newKeyExprs.add((IExpr) m.get(e));
		Pair p = rename(n);
		return new Functional(p.name, p.stage, newKeyExprs, (IExpr) m.get(n.valueExpr));
	}
	@Override
	public Predicate exit(Predicate n, Map<IVisitable, IVisitable> m) {
		List<IExpr> newExprs = new ArrayList<>();
		for (IExpr e : n.exprs) newExprs.add((IExpr) m.get(e));
		Pair p = rename(n);
		return new Predicate(p.name, p.stage, newExprs);
	}
	@Override
	public Entity exit(Entity n, Map<IVisitable, IVisitable> m) {
		List<IExpr> newExprs = new ArrayList<>();
		for (IExpr e : n.exprs) newExprs.add((IExpr) m.get(e));
		Pair p = rename(n);
		return new Entity(p.name, p.stage, newExprs);
	}
	@Override
	public Primitive exit(Primitive n, Map<IVisitable, IVisitable> m) {
		return n;
	}
	@Override
	public RefMode exit(RefMode n, Map<IVisitable, IVisitable> m) {
		Pair p = rename(n);
		return new RefMode(p.name, p.stage, (VariableExpr) m.get(n.entityVar), (IExpr) m.get(n.valueExpr));
	}
	@Override
	public StubAtom exit(StubAtom n, Map<IVisitable, IVisitable> m) {
		Pair p = rename(n);
		return new StubAtom(p.name);
	}


	@Override
	public BinaryExpr exit(BinaryExpr n, Map<IVisitable, IVisitable> m) {
		return new BinaryExpr((IExpr) m.get(n.left), n.op, (IExpr) m.get(n.right));
	}
	@Override
	public ConstantExpr exit(ConstantExpr n, Map<IVisitable, IVisitable> m) {
		return n;
	}
	@Override
	public FunctionalHeadExpr exit(FunctionalHeadExpr n, Map<IVisitable, IVisitable> m) {
		return new FunctionalHeadExpr((Functional) m.get(n.functional));
	}
	@Override
	public GroupExpr exit(GroupExpr n, Map<IVisitable, IVisitable> m) {
		return new GroupExpr((IExpr) m.get(n.expr));
	}
	@Override
	public VariableExpr exit(VariableExpr n, Map<IVisitable, IVisitable> m) {
		return n;
	}


	static class Pair {
		String name;
		String stage;
		Pair(String name, String stage) {
			this.name  = name;
			this.stage = stage;
		}
	}

	Pair rename(IAtom atom) {
		String name = atom.name();

		if (_removeName != null && name.startsWith(_removeName + ":"))
			name = name.replaceFirst(_removeName + ":", "");

		List<Set<String>> fromComps;
		if (_reverseProps == null) fromComps = new ArrayList<>();
		else fromComps =
			_reverseProps.entrySet().stream()
			.filter( entry -> entry.getKey().name().equals(atom.name()) )
			.map( entry -> entry.getValue() )
			.collect(Collectors.toList());
		assert (fromComps.size() <= 1);

		// NOTE: This if should go before the next one, since the heuristic for
		// discovering predicated declared in a component will assume that a
		// @past predicate in the head of the rule is declared in the
		// component.
		if ("@past".equals(atom.stage())) {
			// * we are in the global component, thus in a custom frame rule
			if (_initName == null)
				return new Pair(name + ":past", "@past");
			// * if @past is used in the head of a rule
			// * if @past is used for an entity
			// then fix name accordingly
			else if (_inRuleHead || atom instanceof Entity) {
				if (fromComps.isEmpty())
					return new Pair(name, null);
				else {
					assert (fromComps.get(0).size() == 1);
					String fromComp = fromComps.get(0).iterator().next();
					return new Pair(fromComp + ":" + name, null);
				}
			}
			// * else explicitly add the appropriate prefix and suffix
			else return new Pair(_initName + ":" + name + ":past", "@past");
		}

		// * if the atom is declared in this component, add the appropriate prefix
		if (_declaredAtoms != null && _declaredAtoms.contains(name))
			return new Pair(_initName + ":" + name, atom.stage());

		// * if the atom is propagated from another component, explicitly add
		// the appropriate prefix and suffix
		if (!fromComps.isEmpty())
			return new Pair(_initName + ":" + name + ":past", "@past");

		// * otherwise it is an external atom, thus leave the name unaltered
		return new Pair(name, atom.stage());
	}
}
