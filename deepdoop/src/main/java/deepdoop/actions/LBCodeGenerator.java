package deepdoop.actions;

import deepdoop.datalog.Program;
import deepdoop.datalog.DeepDoopException;
import deepdoop.datalog.component.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LBCodeGenerator {

	Program _p;

	public LBCodeGenerator(Program flatP) {
		PostOrderVisitor<IVisitable> v = new InitVisitingActor();
		Program initP = (Program) flatP.accept(v);

		_p = initP;
	}

	public void generate() {
		AtomCollectingActor acActor = new AtomCollectingActor();
		PostOrderVisitor<IVisitable> acVisitor = new PostOrderVisitor<>(acActor);
		_p.accept(acVisitor);
		Set<String> globalAtomNames = acActor.getDeclaringAtoms(_p.globalComp).keySet();

		// Check that all used predicates have a declaration/definition
		Map<String, Set<String>> reversePropagations = new HashMap<>();
		for (Propagation prop : _p.props) {
			Set<String> fromSet = reversePropagations.get(prop.toId);
			if (fromSet == null) fromSet = new HashSet<>();
			fromSet.add(prop.fromId);
			reversePropagations.put(prop.toId, fromSet);
		}

		Set<String> allDeclAtoms = new HashSet<>(globalAtomNames);
		Set<String> allUsedAtoms = new HashSet<>();
		for (Component c : _p.comps.values()) {
			allDeclAtoms.addAll(acActor.getDeclaringAtoms(c).keySet());
			allUsedAtoms.addAll(acActor.getUsedAtoms(c).keySet());
		}
		for (String usedPred : allUsedAtoms) {
			Set<String> potentialDeclPreds = InitVisitingActor.revert(usedPred, _p.comps.keySet(), reversePropagations);
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
		DependencyGraph graph = new DependencyGraph(_p);

		System.out.println(_p);
		//System.out.println("--------------------");

		//System.out.println("null");
		//System.out.println("D"+acActor.getDeclaringAtoms(_p.globalComp).keySet());
		//System.out.println("U"+acActor.getUsedAtoms(_p.globalComp).keySet());
		//for (Component c : _p.comps.values()) {
		//	System.out.println(c.name);
		//	System.out.println("D"+acActor.getDeclaringAtoms(c).keySet());
		//	System.out.println("U"+acActor.getUsedAtoms(c).keySet());
		//}
	}
}
