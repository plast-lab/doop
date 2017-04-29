package org.clyze.deepdoop.datalog.component

import org.clyze.deepdoop.datalog.element.atom.IAtom

class Propagation {

	static class Alias {
		IAtom orig
		IAtom alias
	}

	String     fromId
	Set<Alias> preds
	String     toId

	Propagation(String fromId, Set<Alias> preds, String toId) {
		this.fromId = fromId
		this.preds  = preds
		this.toId   = toId
	}

	String toString() { "$fromId { $preds } -> $toId" }
}
