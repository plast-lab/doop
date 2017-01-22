package org.clyze.deepdoop.datalog.component;

import java.util.Set;
import org.clyze.deepdoop.datalog.element.atom.IAtom;

public class Propagation {

	public final String     fromId;
	public final Set<IAtom> preds;
	public final String     toId;

	public Propagation(String fromId, Set<IAtom> preds, String toId) {
		this.fromId = fromId;
		this.preds  = preds;
		this.toId   = toId;
	}
}
