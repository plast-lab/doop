package deepdoop.datalog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class Program {

	Component              _global;
	Map<String, Component> _comps;
	Map<String, String>    _inits;
	Set<Propagation>       _props;

	public Program() {
		_comps = new HashMap<>();
		_inits = new HashMap<>();
		_props = new HashSet<>();
	}

	public void global(Component global) {
		_global = global;
	}

	public void comp(Component comp) {
		_comps.put(comp.name, comp);
	}

	public void init(String id, String comp) {
		_inits.put(id, comp);
	}

	public void propagate(String fromId, Set<String> preds, String toId) {
		_props.add(new Propagation(fromId, preds, toId));
	}

	public Component flatten() {
		Component complete = new Component();
		complete.atoms.addAll(_global.atoms);
		complete.rules.addAll(_global.rules);

		Set<String> globalAtoms = _global.getAtoms().keySet();

		// Flatten all components and discard non-initialized ones
		Map<String, Component> flattened = new HashMap<>();
		for (Entry<String, String> entry : _inits.entrySet()) {
			Component flatC = _comps.get(entry.getValue()).flatten(_comps);
			flattened.put(flatC.name, flatC);

			Component initC = flatC.init(new Initializer(entry.getKey(), globalAtoms));
			complete.atoms.addAll(initC.atoms);
			complete.rules.addAll(initC.rules);
		}
		_comps = flattened;

		for (Propagation prop : _props) {
			Component fromFlatC = _comps.get( _inits.get(prop._fromId) );
			Map<String, IAtom> atoms = fromFlatC.getAtoms();

			// Propagate all predicates
			if (prop._preds.isEmpty()) {
				prop._preds.addAll(atoms.keySet());
			}

			for (String pred : prop._preds) {
				IAtom atom = atoms.get(pred);
				if (atom instanceof Directive) continue;

				List<IExpr> vars = new ArrayList<>(atom.arity());
				for (int i = 0 ; i < atom.arity() ; i++) vars.add(new VariableExpr("var" + i));

				IElement head;
				IElement body;
				if (prop._toId == null) {
					// TODO
					//if (globalAtoms.contains(atom.name())) {
					//	System.err.println("Reintroducing " + atom.name() + " to global space");
					//	System.exit(1);
					//}
					head = generate(atom, vars, null, new Initializer(prop._toId, globalAtoms));
				}
				else {
					head = generate(atom, vars, "@past", new Initializer(prop._toId, globalAtoms));
				}
				body = generate(atom, vars, null, new Initializer(prop._fromId, globalAtoms));
				complete.rules.add(new Rule(new LogicalElement(head), body));
			}
		}

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

	static class Propagation {

		String      _fromId;
		Set<String> _preds;
		String      _toId;

		Propagation(String fromId, Set<String> preds, String toId) {
			_fromId = fromId;
			_preds  = preds;
			_toId   = toId;
		}
	}
}
