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
	// For a given (initialized) component get all the predicates that are
	// propagated from a different component (thus need to use the frame
	// rules).
	Set<String>              _propagatedAtoms;

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


		// Component -> Propagated Atoms
		Map<String, Set<String>> propagatedInComp = new HashMap<>();
		n.props.forEach( prop -> {
			Component fromTemplateComp    = n.comps.get(n.inits.get(prop.fromId));
			Map<String, IAtom> declAtoms  = _acActor.getDeclaringAtoms(fromTemplateComp);
			// empty means "*" => propagate everything
			Collection<IAtom> toPropagate = ( prop.preds.isEmpty() ? declAtoms.values() : prop.preds );
			Set<IAtom> newPreds           = new HashSet<>();

			_initName                     = prop.fromId;
			_inRuleHead                   = false;
			_declaredAtoms                = _acActor.getDeclaringAtoms(fromTemplateComp).keySet();

			toPropagate.forEach( pred -> {
				Set<String> set = propagatedInComp.get(prop.toId);
				if (set == null) set = new HashSet<>();
				set.add(pred.name());
				propagatedInComp.put(prop.toId, set);
				Pair p = rename(pred);
				newPreds.add(new StubAtom(p.name));
			});
			initP.addPropagation(prop.fromId, newPreds, prop.toId);
		});


		// Initializations
		n.inits.forEach( (initName, compName) -> {
			Component comp   = n.comps.get(compName);

			if (comp == null)
				ErrorManager.error(ErrorId.UNKNOWN_COMP, compName);

			_removeName      = null;
			_initName        = initName;
			_inRuleHead      = false;
			_declaredAtoms   = _acActor.getDeclaringAtoms(comp).keySet();
			_propagatedAtoms = propagatedInComp.get(initName);
			initP.addComponent((Component) comp.accept(this));
		});
		// TODO why??
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

		// * we are in the global component
		if (_initName == null)
			return new Pair(name, atom.stage());

		// NOTE: This if should go before the next one, since the heuristic for
		// discovering predicated declared in a component will assume that a
		// @past predicate in the head of the rule is declared in the
		// component.
		if ("@past".equals(atom.stage())) {
			// * if @past is used in the head of a rule, leave the name unaltered
			if (_inRuleHead) return new Pair(name, null);
			// * else if @past is used for a declaration, leave the name unaltered
			else if (atom instanceof Entity) return new Pair(name, null);
			// * else explicitly add the appropriate prefix and suffix
			else return new Pair(_initName + ":" + name + ":past", "@past");
		}

		// * if the atom is declared in this component, add the appropriate prefix
		if (_declaredAtoms.contains(name))
			return new Pair(_initName + ":" + name, atom.stage());

		// * if the atom is propagated from another component, explicitly add
		// the appropriate prefix and suffix
		if (_propagatedAtoms != null && _propagatedAtoms.contains(name))
			return new Pair(_initName + ":" + name + ":past", "@past");

		// * otherwise it is an external atom, thus leave the name unaltered
		return new Pair(name, atom.stage());
	}

	String restage(IAtom atom) {
		String stage = atom.stage();
		// * if @past is used in the head of a rule, clear the stage
		return ("@past".equals(stage) && _inRuleHead) ? null : stage;
	}

	// P0 -> P0
	// S1:P1 -> S1:P1
	// S3:P2:past -> S1:P2, S2:P2 when S1 and S2 propagate to S3
	//public static Set<String> revert(String name, Set<String> initIds, Map<String, Set<String>> reversePropagations) {
	//	if (!name.endsWith(":past")) return Collections.singleton(name);

	//	int i = name.indexOf(':');
	//	if (i == -1) return Collections.singleton(name);

	//	String id = name.substring(0, i);
	//	String subName = name.substring(i+1, name.length());

	//	if (reversePropagations.get(id) != null) {
	//		subName = subName.substring(0, subName.lastIndexOf(":past"));
	//		Set<String> fromSet = reversePropagations.get(id);
	//		Set<String> result = new HashSet<>();
	//		for (String fromId : fromSet) {
	//			result.add(fromId + ":" + subName);
	//		}
	//		return result;
	//	}
	//	else
	//		return Collections.singleton(subName);
	//}
}
