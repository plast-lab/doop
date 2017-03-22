package org.clyze.deepdoop.datalog.clause

import org.clyze.deepdoop.actions.*
import org.clyze.deepdoop.datalog.element.atom.IAtom
import org.clyze.deepdoop.system.*

class Declaration implements IVisitable, ISourceItem {

	IAtom       atom
	List<IAtom> types

	Declaration(IAtom atom, Set<IAtom> types) {
		this.atom  = atom
		this.types = []
		this.loc   = SourceManager.v().getLastLoc()

		def varsInHead = atom.getVars()
		types.each{ t ->
			def vars = t.getVars()
			assert vars.size() == 1
			def index = varsInHead.indexOf(vars.get(0))
			if (index == -1)
				ErrorManager.error(location(), ErrorId.UNKNOWN_VAR, vars.get(0).name)
			this.types[index] = t
		}
		assert (types.size() == 0 || types.size() == varsInHead.size())
	}

	def <T> T accept(IVisitor<T> v) { v.visit(this) }

	String toString() {
		"$atom -> ${types.collect{ it.toString() }.join(',')}."
	}

	SourceLocation loc
	SourceLocation location() { loc }
}