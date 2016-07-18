package deepdoop.actions;

import deepdoop.datalog.*;
import deepdoop.datalog.clause.*;
import deepdoop.datalog.component.*;
import deepdoop.datalog.element.*;
import deepdoop.datalog.element.atom.*;
import deepdoop.datalog.expr.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LBCodeGenActor implements IActor<IVisitable> {

	AtomCollectingActor _acActor;
	Set<String>         _globalAtomNames;

	public LBCodeGenActor() {
		_acActor = new AtomCollectingActor();
	}

	@Override
	public void enter(Program n) {
		// Check that all used predicates have a declaration/definition
		Map<String, Set<String>> reversePropagations = new HashMap<>();
		for (Propagation prop : n.props) {
			Set<String> fromSet = reversePropagations.get(prop.toId);
			if (fromSet == null) fromSet = new HashSet<>();
			fromSet.add(prop.fromId);
			reversePropagations.put(prop.toId, fromSet);
		}

		PostOrderVisitor<IVisitable> visitor = new PostOrderVisitor<>(_acActor);
		n.accept(visitor);
		_globalAtomNames = _acActor.getDeclaringAtoms(n.globalComp).keySet();

		Set<String> allDeclAtoms = new HashSet<>(_globalAtomNames);
		Set<String> allUsedAtoms = new HashSet<>();
		for (Component c : n.comps.values()) {
			allDeclAtoms.addAll(_acActor.getDeclaringAtoms(c).keySet());
			allUsedAtoms.addAll(_acActor.getUsedAtoms(c).keySet());
		}
		for (String usedPred : allUsedAtoms) {
			Set<String> potentialDeclPreds = InitVisitingActor.revert(usedPred, n.comps.keySet(), reversePropagations);
			boolean declFound = false;
			for (String potentialDeclPred : potentialDeclPreds) {
				if (allDeclAtoms.contains(potentialDeclPred)) {
					declFound = true;
					break;
				}
			}
			if (!declFound)
				throw new DeepDoopException("Predicate `" + usedPred + "` used but not declared");
		}

		// Compute dependency graph for components (and global predicates)
		DependencyGraph g = new DependencyGraph(n);
	}
	@Override
	public Program exit(Program n, Map<IVisitable, IVisitable> m) {
		Program finalP = new Program(new Component(n.globalComp), null, null, null);
		for (Component c : n.comps.values()) finalP.globalComp.addAll(c);

		for (Propagation prop : n.props) {
			Component fromComp  = n.comps.get(prop.fromId);
			Map<String, IAtom> declAtoms = _acActor.getDeclaringAtoms(fromComp);

			for (String predName : prop.preds) {
				IAtom atom = declAtoms.get(predName);
				if (atom instanceof Directive) continue;

				List<VariableExpr> vars = new ArrayList<>(atom.arity());
				for (int i = 0 ; i < atom.arity() ; i++) vars.add(new VariableExpr("var" + i));

				// Propagation to global scope
				if (prop.toId == null && _globalAtomNames.contains(atom.name()))
					throw new DeepDoopException("Reintroducing predicate '" + atom.name() + "' to global space");

				IElement head = (IAtom) atom.instantiate((prop.toId == null ? null : "@past"), vars).
					accept(new InitVisitingActor(prop.fromId, prop.toId, _globalAtomNames));
				IElement body = (IAtom) atom.instantiate(null, vars);
				finalP.globalComp.addRule(new Rule(new LogicalElement(head), body));
			}
		}
		return finalP;
	}

	@Override
	public CmdComponent exit(CmdComponent n, Map<IVisitable, IVisitable> m) {
		return n;
	}
	@Override
	public Component exit(Component n, Map<IVisitable, IVisitable> m) {
		return n;
	}
}
