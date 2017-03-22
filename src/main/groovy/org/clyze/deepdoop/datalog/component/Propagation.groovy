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

	String toString() { "$fromId { $preds } -> $toId" }
}