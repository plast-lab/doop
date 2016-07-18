package deepdoop.datalog;

import deepdoop.actions.IVisitable;
import deepdoop.actions.IVisitor;
import deepdoop.datalog.component.*;
import deepdoop.datalog.element.atom.IAtom;
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

	public Program(Component globalComp, Map<String, Component> comps, Map<String, String> inits, Set<Propagation> props) {
		this.globalComp = (globalComp != null ? globalComp : new Component());
		this.comps      = (comps != null ? comps : new HashMap<>());
		this.inits      = (inits != null ? inits : new HashMap<>());
		this.props      = (props != null ? props : new HashSet<>());
	}
	public Program() {
		this(null, null, null, null);
	}

	public void addComponent(Component comp) {
		comps.put(comp.name, comp);
	}
	public void addInit(String id, String comp) {
		if (inits.get(id) != null)
			throw new DeepDoopException("Id `" + id + "` already used to initialize a component");

		inits.put(id, comp);
	}
	public void addPropagation(String fromId, Set<IAtom> preds, String toId) {
		props.add(new Propagation(fromId, preds, toId));
	}

	@Override
	public String toString() {
		StringJoiner joiner = new StringJoiner("\n");
		joiner.add(globalComp.toString());
		for (Component c : comps.values()) joiner.add(c.toString());
		return joiner.toString();
	}


	@Override
	public <T> T accept(IVisitor<T> v) {
		return v.visit(this);
	}
}
