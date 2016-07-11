package deepdoop.datalog;

import deepdoop.actions.IVisitable;
import deepdoop.actions.IVisitor;
import deepdoop.datalog.component.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

public class Program implements IVisitable {

	public Component                    globalComp;
	public final Map<String, Component> comps;
	public final Map<String, String>    inits;
	public final Set<Propagation>       props;

	public Program() {
		comps = new HashMap<>();
		inits = new HashMap<>();
		props = new HashSet<>();
	}

	public void setGlobalComp(Component globalComp) {
		assert this.globalComp == null;
		this.globalComp = globalComp;
	}
	public void addComp(Component comp) {
		comps.put(comp.name, comp);
	}
	public void addInit(String id, String comp) {
		if (inits.get(id) != null)
			throw new DeepDoopException("Id `" + id + "` already used to initialize a component");

		inits.put(id, comp);
	}
	public void addPropagate(String fromId, Set<String> preds, String toId) {
		props.add(new Propagation(fromId, preds, toId));
	}

	@Override
	public IVisitable accept(IVisitor v) {
		v.enter(this);
		Map<IVisitable, IVisitable> m = new HashMap<>();
		m.put(globalComp, globalComp.accept(v));
		for (Component c : comps.values()) m.put(c, c.accept(v));
		return v.exit(this, m);
	}

	@Override
	public String toString() {
		StringJoiner joiner = new StringJoiner("\n");
		joiner.add(globalComp.toString());
		for (Component c : comps.values()) joiner.add(c.toString());
		return joiner.toString();
	}

//	public Component flatten() {
//		// Check that all used predicates have a declaration/definition
//		Map<String, Set<String>> reversePropsMap = new HashMap<>();
//		for (Propagation prop : props) {
//			Set<String> fromSet = reversePropsMap.get(prop.toId);
//			if (fromSet == null) fromSet = new HashSet<>();
//			fromSet.add(prop.fromId);
//			reversePropsMap.put(prop.toId, fromSet);
//		}
//
//		Set<String> allDeclAtoms = new HashSet<>(globalComp.getDeclaringAtoms().keySet());
//		Set<String> allInAtoms = new HashSet<>();
//		for (Component c : initComps.values()) {
//			allDeclAtoms.addAll(c.getDeclaringAtoms().keySet());
//			allInAtoms.addAll(c.getInputAtoms().keySet());
//		}
//		for (String inputPred : allInAtoms) {
//			Set<String> potentialDeclPreds = Initializer.revert(inputPred, initComps.keySet(), reversePropsMap);
//			boolean declFound = false;
//			for (String potentialDeclPred : potentialDeclPreds)
//				if (allDeclAtoms.contains(potentialDeclPred)) {
//					declFound = true;
//					break;
//				}
//			if (!declFound)
//				throw new DeepDoopException("Predicate `" + inputPred + "` used but not declared");
//		}
//
//
//		// Compute dependency graph for initialized components (and global
//		// predicates)
//		DependencyGraph g = new DependencyGraph(props, initComps, globalComp);
//
//
//		return complete;
//	}
}
