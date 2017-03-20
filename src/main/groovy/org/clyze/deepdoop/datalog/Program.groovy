package org.clyze.deepdoop.datalog

import org.clyze.deepdoop.actions.*
import org.clyze.deepdoop.datalog.component.*
import org.clyze.deepdoop.datalog.element.atom.IAtom
import org.clyze.deepdoop.system.*

class Program implements IVisitable {

	public Component                    globalComp
	public final Map<String, Component> comps
	public final Map<String, String>    inits
	public final Set<Propagation>       props

	Program(Component globalComp, Map<String, Component> comps, Map<String, String> inits, Set<Propagation> props) {
		this.globalComp = globalComp
		this.comps      = comps as Map
		this.inits      = inits as Map
		this.props      = props as Set
	}
	Program() {
		this(new Component(), [:], [:], [] as Set)
	}

	void addComponent(Component comp) {
		comps.put(comp.name, comp)
	}
	void addInit(String id, String comp) {
		if (inits.get(id) != null)
			ErrorManager.error(ErrorId.ID_IN_USE, id)
		inits[id] = comp
	}
	void addPropagation(String fromId, Set<IAtom> preds, String toId) {
		props.add(new Propagation(fromId, preds, toId))
	}


	@Override
	<T> T accept(IVisitor<T> v) { return v.visit(this) }

	String toString() {
		return globalComp.toString() +
				(comps.collect{ k, v -> v.toString() } +
				 inits.collect{ k, v -> "$v as $k".toString() } +
				 props.collect{ it.toString() }).join('\n')
	}


	static Program from(Component globalComp, Map<String, Component> comps, Map<String, String> inits, Set<Propagation> props) {
		return new Program(
			globalComp == null ?: new Component(globalComp),
			comps      == null ?: [:] << comps,
			inits      == null ?: [:] << inits,
			props      == null ?: props.collect() as Set
			)
	}
}
