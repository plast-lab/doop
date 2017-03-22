package org.clyze.deepdoop.datalog

import org.clyze.deepdoop.actions.*
import org.clyze.deepdoop.datalog.component.*
import org.clyze.deepdoop.system.*

class Program implements IVisitable {

	Component              globalComp
	Map<String, Component> comps
	Map<String, String>    inits
	Set<Propagation>       props

	Program(Component globalComp, Map<String, Component> comps, Map<String, String> inits, Set<Propagation> props) {
		this.globalComp = globalComp
		this.comps      = comps
		this.inits      = inits
		this.props      = props
	}
	Program() {
		this(new Component(), [:], [:], [] as Set)
	}

	void addComponent(Component comp) {
		comps[comp.name] = comp
	}
	void addInit(String id, String comp) {
		if (inits.get(id) != null)
			ErrorManager.error(ErrorId.ID_IN_USE, id)
		inits[id] = comp
	}
	void addPropagation(Propagation prop) {
		props << prop
	}

	def <T> T accept(IVisitor<T> v) { v.visit(this) }

	String toString() {
		globalComp.toString() +
				(comps.collect{ k, v -> v.toString() } +
				 inits.collect{ k, v -> "$v as $k".toString() } +
				 props.collect{ it.toString() }).join('\n')
	}


	static Program from(Component globalComp, Map<String, Component> comps, Map<String, String> inits, Set<Propagation> props) {
		return new Program(
				globalComp ?: new Component(globalComp),
				comps ?: [:] << comps,
				inits ?: [:] << inits,
				props ?: [] + props as Set)
	}
}