package org.clyze.deepdoop.datalog

import groovy.transform.Canonical
import org.clyze.deepdoop.actions.*
import org.clyze.deepdoop.datalog.component.*
import org.clyze.deepdoop.system.*

@Canonical
class Program implements IVisitable {

	Component              globalComp = new Component()
	Map<String, Component> comps = [:]
	Map<String, String>    inits = [:]
	Set<Propagation>       props = [] as Set

	void addComponent(Component comp) {
		comps[comp.name] = comp
	}
	void addInit(String id, String comp) {
		if (inits[id]) ErrorManager.error(ErrorId.ID_IN_USE, id)
		inits[id] = comp
	}
	void addPropagation(Propagation prop) {
		props << prop
	}

	def <T> T accept(IVisitor<T> v) { v.visit(this) }

	String toString() {
		(globalComp as String) +
		(comps.collect{ it.value as String } +
		 inits.collect{ "$it.value as $it.key" as String } +
		 props.collect{ it as String }).join('\n')
	}
}
