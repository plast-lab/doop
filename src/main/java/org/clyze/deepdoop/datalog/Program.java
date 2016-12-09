package org.clyze.deepdoop.datalog;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.clyze.deepdoop.actions.IVisitable;
import org.clyze.deepdoop.actions.IVisitor;
import org.clyze.deepdoop.datalog.component.*;
import org.clyze.deepdoop.datalog.element.atom.IAtom;
import org.clyze.deepdoop.system.Error;
import org.clyze.deepdoop.system.ErrorManager;

public class Program implements IVisitable {

	public Component                    globalComp;
	public final Map<String, Component> comps;
	public final Map<String, String>    inits;
	public final Set<Propagation>       props;

	public Program(Component globalComp, Map<String, Component> comps, Map<String, String> inits, Set<Propagation> props) {
		this.globalComp = globalComp;
		this.comps      = comps;
		this.inits      = inits;
		this.props      = props;
	}
	public Program() {
		this(new Component(), new HashMap<>(), new HashMap<>(), new HashSet<>());
	}

	public void addComponent(Component comp) {
		comps.put(comp.name, comp);
	}
	public void addInit(String id, String comp) {
		if (inits.get(id) != null)
			ErrorManager.v().error(Error.ID_IN_USE, id);
		inits.put(id, comp);
	}
	public void addPropagation(String fromId, Set<IAtom> preds, String toId) {
		props.add(new Propagation(fromId, preds, toId));
	}


	@Override
	public <T> T accept(IVisitor<T> v) {
		return v.visit(this);
	}


	public static Program from(Component globalComp, Map<String, Component> comps, Map<String, String> inits, Set<Propagation> props) {
		return new Program(
				globalComp == null ? null : new Component(globalComp),
				comps      == null ? null : new HashMap<>(comps),
				inits      == null ? null : new HashMap<>(inits),
				props      == null ? null : new HashSet<>(props)
				);
	}
}
