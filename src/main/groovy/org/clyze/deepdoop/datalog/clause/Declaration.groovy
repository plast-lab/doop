package org.clyze.deepdoop.datalog.clause

import org.clyze.deepdoop.actions.*
import org.clyze.deepdoop.datalog.element.atom.IAtom
import org.clyze.deepdoop.system.*

class Declaration implements IVisitable, ISourceItem {

	public final IAtom       atom
	public final List<IAtom> types

	Declaration(IAtom atom, Set<IAtom> types) {
		this.atom = atom
		this._loc = SourceManager.v().getLastLoc()

		def varsInHead = atom.getVars()
		def typesCount = types.size()
		def ordered = new IAtom[typesCount]
		types.each{ t ->
			def vars = t.getVars()
			assert vars.size() == 1
			def index = varsInHead.indexOf(vars.get(0))
			if (index == -1)
				ErrorManager.error(location(), ErrorId.UNKNOWN_VAR, vars.get(0).name)
			ordered[index] = t
		}
		assert (typesCount == 0 || typesCount == varsInHead.size())

		this.types = new ArrayList<>(Arrays.asList(ordered))
	}


	@Override
	<T> T accept(IVisitor<T> v) { return v.visit(this) }

	String toString() {
		def typeStr = types.collect{ it.toString() }.join(',')
		return "$atom -> $typeStr."
	}

	SourceLocation _loc
	SourceLocation location() { return _loc }
}
