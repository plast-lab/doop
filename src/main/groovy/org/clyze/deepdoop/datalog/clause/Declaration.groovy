package org.clyze.deepdoop.datalog.clause

import groovy.transform.Canonical
import org.clyze.deepdoop.actions.*
import org.clyze.deepdoop.datalog.element.atom.IAtom
import org.clyze.deepdoop.system.*

@Canonical
class Declaration implements IVisitable, TSourceItem {

	IAtom atom
	List<IAtom> types

	Declaration(IAtom atom, List<IAtom> types) {
		this.atom = atom
		this.types = []

		def varsInHead = atom.getVars()
		types.each{ t ->
			def vars = t.getVars()
			assert vars.size() == 1
			def index = varsInHead.indexOf(vars.get(0))
			if (index == -1)
				ErrorManager.error(loc, ErrorId.UNKNOWN_VAR, vars.get(0).name)
			this.types[index] = t
		}
		assert (types.size() == 0 || types.size() == varsInHead.size())
	}

	def <T> T accept(IVisitor<T> v) { v.visit(this) }
}
