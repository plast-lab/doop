package org.clyze.deepdoop.datalog.component

import groovy.transform.Canonical
import org.clyze.deepdoop.datalog.element.atom.IAtom

@Canonical
class Propagation {

	@Canonical
	static class Alias {
		IAtom orig
		IAtom alias
	}

	String fromId
	Set<Alias> preds
	String toId
}
