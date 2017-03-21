package org.clyze.deepdoop.datalog.component

import org.clyze.deepdoop.datalog.element.atom.IAtom

class Propagation {

	public final String     fromId
	public final Set<IAtom> preds
	public final String     toId

	Propagation(String fromId, Set<IAtom> preds, String toId) {
		this.fromId = fromId
		this.preds  = preds
		this.toId   = toId
	}

	String toString() { return "$fromId -- $preds --> $toId" }
}
