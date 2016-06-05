package deepdoop.datalog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class Program {

	Map<String, Component> _templateComps;
	Map<String, String>    _inits;
	Component              _globalComp;
	Set<Propagation>       _props;

	public Program() {
		_templateComps = new HashMap<>();
		_inits         = new HashMap<>();
		_props         = new HashSet<>();
	}

	public void global(Component globalComp) {
		assert _globalComp == null;
		_globalComp = globalComp;
	}

	public void comp(Component comp) {
		_templateComps.put(comp.name, comp);
	}

	public void init(String id, String comp) {
		if (_inits.get(id) != null)
			throw new DeepDoopException("ERROR: Id `" + id + "` already used to initialize a component");

		_inits.put(id, comp);
	}

	public void propagate(String fromId, Set<String> preds, String toId) {
		_props.add(new Propagation(fromId, preds, toId));
	}

	public Component flatten() {
		// Flatten template components
		Map<String, Component> flatComps = new HashMap<>();
		for (Entry<String, Component> entry : _templateComps.entrySet()) {
			String compName = entry.getKey();
			Component c     = entry.getValue();
			Component flatC = c.flatten(_templateComps);
			flatComps.put(compName, flatC);
		}
		_templateComps = flatComps;


		Component complete = new Component(_globalComp);
		Map<String, IAtom> globalAtoms = _globalComp.getDeclaringAtoms();

		// Initialize components
		Map<String, Component> initComps = new HashMap<>();
		for (Entry<String, String> entry : _inits.entrySet()) {
			String initName = entry.getKey();
			String compName = entry.getValue();
			Component c     = _templateComps.get(compName);
			Component initC = c.init(new Initializer(initName, globalAtoms.keySet()));
			initComps.put(initName, initC);
			complete.addAll(initC);
		}

		// Handle propagations
		for (Propagation prop : _props) {
			String fromTemplate = _inits.get(prop.fromId);
			String toTemplate   = _inits.get(prop.toId);
			Component fromComp  = _templateComps.get(fromTemplate);
			Component toComp    = _templateComps.get(toTemplate);
			Map<String, IAtom> atoms  = fromComp.getDeclaringAtoms();

			// Propagate all predicates (*)
			if (prop.preds.isEmpty()) {
				prop.preds.addAll(atoms.keySet());
			}

			for (String predName : prop.preds) {
				IAtom atom = atoms.get(predName);
				if (atom instanceof Directive) continue;

				List<IExpr> vars = new ArrayList<>(atom.arity());
				for (int i = 0 ; i < atom.arity() ; i++) vars.add(new VariableExpr("var" + i));

				IElement head;
				// Propagation to global scope
				if (prop.toId == null && globalAtoms.containsKey(atom.name()))
					throw new DeepDoopException("Reintroducing predicate '" + atom.name() + "' to global space");
				else
					head = generate(atom, vars, (prop.toId == null ? null : "@past"), new Initializer(prop.toId, globalAtoms.keySet()));
				IElement body = generate(atom, vars, null, new Initializer(prop.fromId, globalAtoms.keySet()));
				complete.addRule(new Rule(new LogicalElement(head), body));
			}
		}


		// Check that all used predicates have a declaration/definition
		Map<String, Set<String>> reversePropsMap = new HashMap<>();
		for (Propagation prop : _props) {
			Set<String> fromSet = reversePropsMap.get(prop.toId);
			if (fromSet == null) fromSet = new HashSet<>();
			fromSet.add(prop.fromId);
			reversePropsMap.put(prop.toId, fromSet);
		}

		Set<String> allDeclAtoms = new HashSet<>(_globalComp.getDeclaringAtoms().keySet());
		Set<String> allInAtoms = new HashSet<>();
		for (Component c : initComps.values()) {
			allDeclAtoms.addAll(c.getDeclaringAtoms().keySet());
			allInAtoms.addAll(c.getInputAtoms().keySet());
		}
		for (String inputPred : allInAtoms) {
			Set<String> potentialDeclPreds = Initializer.revert(inputPred, initComps.keySet(), reversePropsMap);
			boolean declFound = false;
			for (String potentialDeclPred : potentialDeclPreds)
				if (allDeclAtoms.contains(potentialDeclPred)) {
					declFound = true;
					break;
				}
			if (!declFound)
				throw new DeepDoopException("Predicate `" + inputPred + "` used but not declared");
		}


		// Compute dependency graph for initialized components (and global
		// predicates)
		DependencyGraph g = new DependencyGraph(_props, initComps, _globalComp);


		return complete;
	}

	@Override
	public String toString() {
		return flatten().toString();
	}


	static IElement generate(IAtom atom, List<IExpr> vars, String stage, Initializer ini) {
		if      (atom instanceof Predicate)
			return new Predicate(atom.name(), stage, vars).init(ini);
		else if (atom instanceof Functional) {
			VariableExpr valueVar = (VariableExpr) vars.remove(vars.size()-1);
			return new Functional(atom.name(), stage, vars, valueVar).init(ini);
		}
		else if (atom instanceof RefMode)
			return new RefMode(atom.name(), stage, (VariableExpr)vars.get(0), vars.get(1)).init(ini);
		else if (atom instanceof Directive)
			return ((Directive)atom).init(ini);
		else
			return null;
	}
}
