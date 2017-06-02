package org.clyze.deepdoop.datalog.clause

import groovy.transform.Canonical
import org.clyze.deepdoop.actions.IVisitable
import org.clyze.deepdoop.actions.IVisitor
import org.clyze.deepdoop.datalog.Annotation
import org.clyze.deepdoop.datalog.element.atom.IAtom
import org.clyze.deepdoop.system.TSourceItem

@Canonical
class Declaration implements IVisitable, TSourceItem {

	IAtom atom
	List<IAtom> types
	List<Annotation> annotations

	def <T> T accept(IVisitor<T> v) { v.visit(this) }
}
