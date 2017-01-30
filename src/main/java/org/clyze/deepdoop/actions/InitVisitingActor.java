package org.clyze.deepdoop.actions;

import java.util.ArrayList;
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

	Renamer             _r;
	AtomCollectingActor _acActor;

	public InitVisitingActor() {
		// Implemented this way, because Java doesn't allow usage of "this"
		// keyword before all implicit/explicit calls to super/this have
		// returned
		super(null);
		_actor = this;

		_r = new Renamer(null, null, null);
	}


	// Need to be overriden because a component might have to be visited
	// multiple times (if initialized multiple times)
	@Override
	public IVisitable visit(Program n) {
		_acActor = new AtomCollectingActor();
		PostOrderVisitor<IVisitable> acVisitor = new PostOrderVisitor<>(_acActor);
		n.accept(acVisitor);

		Set<String> globalDeclAtoms = new HashSet<>(_acActor.getDeclaringAtoms(n.globalComp).keySet());
		Set<String> globalAtoms     = new HashSet<>(globalDeclAtoms);
		globalAtoms.addAll(_acActor.getUsedAtoms(n.globalComp).keySet());

		// Global Component
		Component newGlobal = (Component) n.globalComp.accept(this);

		// Initializations
		final Program initP = Program.from(newGlobal, new HashMap<>(), null, new HashSet<>());
		n.inits.forEach( (initName, compName) -> {
			Component comp = n.comps.get(compName);
			_r.reset(null, initName, externalAtoms(comp));
			initP.addComponent((Component) comp.accept(this));
		});
		initP.accept(acVisitor);

		// Propagations
		for (Propagation prop : n.props) {
			String from                  = prop.fromId;
			String to                    = prop.toId;
			Component fromComp           = initP.comps.get(from);
			Component toComp             = (to == null ? initP.globalComp : initP.comps.get(to));
			Map<String, IAtom> declAtoms = _acActor.getDeclaringAtoms(fromComp);
			Set<IAtom> newPreds          = new HashSet<>();

			_r.reset(null, from, externalAtoms(fromComp));
			// empty means "*" => propagate everything
			if (prop.preds.isEmpty())
				newPreds.addAll(declAtoms.values());
			else
				prop.preds.forEach(pred -> {
					String newName = _r.rename(pred.name());
					newPreds.add(declAtoms.get(newName));
				});
			initP.addPropagation(from, newPreds, to);

			_r.reset(from, to, null);
			// Generate frame rules
			for (IAtom atom : newPreds) {
				if (atom instanceof Directive) continue;

				List<VariableExpr> vars = VariableExpr.genTempVars(atom.arity());

				// Propagate to global scope
				if (to == null) {
					String newName = _r.rename(atom.name());
					// Declared in global space
					if (globalDeclAtoms.contains(newName))
						ErrorManager.error(ErrorId.DEP_GLOBAL, newName);
					// Used in global space (but not declared there)
					// * might be declared inside a component and then propagated to global
					// * might be declared in a different (previous) file
					else if (globalAtoms.contains(newName))
						ErrorManager.warn(ErrorId.DEP_GLOBAL, newName);
				}

				String stage = (to == null ? null : "@past");
				IElement head = (IAtom) atom.instantiate(stage, vars).accept(this);
				IElement body = (IAtom) atom.instantiate(null, vars);
				toComp.addRule(new Rule(new LogicalElement(head), body));
			}
		}
		return initP;
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
		return new CmdComponent(_r.addId(), newDeclarations, n.eval, newImports, newExports);
	}
	@Override
	public Component exit(Component n, Map<IVisitable, IVisitable> m) {
		Component newComp = new Component(_r.addId());
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
		return new Rule((LogicalElement) m.get(n.head), (IElement) m.get(n.body));
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
		return new Functional(_r.rename(n.name, n.stage), _r.restage(n.stage), newKeyExprs, (IExpr) m.get(n.valueExpr));
	}
	@Override
	public Predicate exit(Predicate n, Map<IVisitable, IVisitable> m) {
		List<IExpr> newExprs = new ArrayList<>();
		for (IExpr e : n.exprs) newExprs.add((IExpr) m.get(e));
		return new Predicate(_r.rename(n.name, n.stage), _r.restage(n.stage), newExprs);
	}
	@Override
	public Primitive exit(Primitive n, Map<IVisitable, IVisitable> m) {
		return n;
	}
	@Override
	public RefMode exit(RefMode n, Map<IVisitable, IVisitable> m) {
		return new RefMode(_r.rename(n.name), _r.restage(n.stage), (VariableExpr) m.get(n.entityVar), (IExpr) m.get(n.valueExpr));
	}
	@Override
	public StubAtom exit(StubAtom n, Map<IVisitable, IVisitable> m) {
		return new StubAtom(_r.rename(n.name));
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


	Set<String> externalAtoms(Component n) {
		Set<String> declAtoms = _acActor.getDeclaringAtoms(n).keySet();
		Set<String> usedAtoms = _acActor.getUsedAtoms(n).keySet();
		// Atoms that are used but not declared in the component, are external
		Set<String> externals = new HashSet<>(usedAtoms);
		externals.removeAll(declAtoms);
		return externals;
	}
}
