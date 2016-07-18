package deepdoop.actions;

import deepdoop.datalog.Program;
import deepdoop.datalog.clause.*;
import deepdoop.datalog.component.*;
import deepdoop.datalog.element.*;
import deepdoop.datalog.element.atom.*;
import deepdoop.datalog.expr.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class InitVisitingActor extends PostOrderVisitor<IVisitable> implements IActor<IVisitable> {

	String      _oldId;
	String      _id;
	Set<String> _globalAtomNames;

	public InitVisitingActor(String oldId, String id, Set<String> globalAtomNames) {
		super(null);             // TODO FIX ugly?
		_actor           = this; // TODO FIX ugly?
		_oldId           = oldId;
		_id              = id;
		_globalAtomNames = globalAtomNames;
	}
	public InitVisitingActor() {
		this(null, null, null);
	}

	String name(String name, String stage) {
		// *In* global space
		if (_globalAtomNames.contains(name)) {
			assert !"@past".equals(stage);
			return name;
		}
		// Propagate *to* global space
		else if (_id == null) {
			assert _oldId != null;
			assert name.startsWith(_oldId + ":");
			return name.replaceFirst(_oldId + ":", "");
		}
		// Propagate between components
		else {
			if (_oldId != null) {
				assert _oldId != null;
				assert name.startsWith(_oldId + ":");
				name = name.replaceFirst(_oldId + ":", "");
			}
			return _id + ":" + name + ("@past".equals(stage) ? ":past" : "");
		}
	}
	String name(String name) {
		return name(name, null);
	}
	String stage(String stage) {
		return ("@past".equals(stage) ? null : stage);
	}


	@Override
	public IVisitable visit(Program n) {
		AtomCollectingActor acActor = new AtomCollectingActor();
		PostOrderVisitor<IVisitable> visitor = new PostOrderVisitor<>(acActor);
		n.accept(visitor);
		_globalAtomNames = acActor.getDeclaringAtoms(n.globalComp).keySet();

		Program initP = new Program(n.globalComp, null, null, null);
		for (Entry<String, String> entry : n.inits.entrySet()) {
			String initName = entry.getKey();
			String compName = entry.getValue();
			Component c     = n.comps.get(compName);
			_id             = initName;
			initP.addComponent((Component) c.accept(this));
		}

		initP.accept(visitor);
		for (Propagation prop : n.props) {
			_id                          = prop.fromId;
			Component fromComp           = initP.comps.get(prop.fromId);
			Map<String, IAtom> declAtoms = acActor.getDeclaringAtoms(fromComp);
			Set<IAtom> newPreds          = new HashSet<>();

			// Propagate all predicates (*)
			if (prop.preds.isEmpty())
				newPreds.addAll(declAtoms.values());
			else
				for (IAtom pred : prop.preds)
					newPreds.add(declAtoms.get(name(pred.name())));

			initP.addPropagation(prop.fromId, newPreds, prop.toId);
		}
		return initP;
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
	public CmdComponent exit(CmdComponent n, Map<IVisitable, IVisitable> m) {
		Set<Declaration> newDeclarations = new HashSet<>();
		for (Declaration d : n.declarations) newDeclarations.add((Declaration) m.get(d));
		Set<StubAtom> newImports = new HashSet<>();
		for (StubAtom p : n.imports) newImports.add((StubAtom) m.get(p));
		Set<StubAtom> newExports = new HashSet<>();
		for (StubAtom p : n.exports) newExports.add((StubAtom) m.get(p));
		return new CmdComponent(_id, newDeclarations, n.eval, n.dir, newImports, newExports);
	}
	@Override
	public Component exit(Component n, Map<IVisitable, IVisitable> m) {
		Component newComp = new Component(_id);
		for (Declaration d : n.declarations) newComp.declarations.add((Declaration) m.get(d));
		for (Constraint c : n.constraints)   newComp.constraints.add((Constraint) m.get(c));
		for (Rule r : n.rules)               newComp.rules.add((Rule) m.get(r));
		return newComp;
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
		return new Functional(name(n.name, n.stage), stage(n.stage), newKeyExprs, (IExpr) m.get(n.valueExpr));
	}
	@Override
	public Predicate exit(Predicate n, Map<IVisitable, IVisitable> m) {
		List<IExpr> newExprs = new ArrayList<>();
		for (IExpr e : n.exprs) newExprs.add((IExpr) m.get(e));
		return new Predicate(name(n.name, n.stage), stage(n.stage), newExprs);
	}
	@Override
	public Primitive exit(Primitive n, Map<IVisitable, IVisitable> m) {
		return n;
	}
	@Override
	public RefMode exit(RefMode n, Map<IVisitable, IVisitable> m) {
		return new RefMode(name(n.name), stage(n.stage), (VariableExpr) m.get(n.entityVar), (IExpr) m.get(n.valueExpr));
	}
	@Override
	public StubAtom exit(StubAtom n, Map<IVisitable, IVisitable> m) {
		return new StubAtom(name(n.name));
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


	// P0 -> P0
	// S1:P1 -> S1:P1
	// S3:P2:past -> S1:P2, S2:P2 when S1 and S2 propagate to S3
	public static Set<String> revert(String name, Set<String> initIds, Map<String, Set<String>> reversePropagations) {
		if (!name.endsWith(":past")) return Collections.singleton(name);

		int i = name.indexOf(':');
		if (i == -1) return Collections.singleton(name);

		String id = name.substring(0, i);
		String subName = name.substring(i+1, name.length());

		if (reversePropagations.get(id) != null) {
			subName = subName.substring(0, subName.lastIndexOf(":past"));
			Set<String> fromSet = reversePropagations.get(id);
			Set<String> result = new HashSet<>();
			for (String fromId : fromSet) {
				result.add(fromId + ":" + subName);
			}
			return result;
		}
		else
			return Collections.singleton(subName);
	}
}
