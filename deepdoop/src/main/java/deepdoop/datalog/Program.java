package deepdoop.datalog;

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

		// Flatten all components and discard non-initialized ones
		Map<String, Component> flattened = new HashMap<>();
		for (Entry<String, String> entry : _inits.entrySet()) {
			Component flatC = _comps.get(entry.getValue()).flatten(_comps);
			flattened.put(flatC.name, flatC);

			Component initC = flatC.init(entry.getKey());
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
				List<IExpr> vars = Names.newVars(atom.arity());
				complete.rules.add(new Rule(
							generate(atom, vars, prop._toId, "@past"),
							generate(atom, vars, prop._fromId, null)));
			}
		}

		return complete;
	}

	@Override
	public String toString() {
		return flatten().toString();
	}


	static IElement generate(IAtom atom, List<IExpr> vars, String id, String stage) {
		String name = Names.nameId(atom.name(), id);
		switch (atom.type()) {
			case PREDICATE :
				return new PredicateElement(name, stage, vars);
			case FUNCTIONAL:
				VariableExpr value = (VariableExpr) vars.remove(vars.size()-1);
				return new FunctionalElement(name, stage, vars, value);
			case REFMODE   :
				return new RefModeElement(name, stage, (VariableExpr) vars.get(0), vars.get(1));
		}
		return null;
	}
}


class Propagation {

	String      _fromId;
	Set<String> _preds;
	String      _toId;

	Propagation(String fromId, Set<String> preds, String toId) {
		_fromId = fromId;
		_preds  = preds;
		_toId   = toId;
	}
}
