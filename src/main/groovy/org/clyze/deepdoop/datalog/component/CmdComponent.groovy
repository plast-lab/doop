package org.clyze.deepdoop.datalog.component

import groovy.transform.Canonical
import org.clyze.deepdoop.actions.*
import org.clyze.deepdoop.datalog.clause.*
import org.clyze.deepdoop.datalog.element.atom.*
import org.clyze.deepdoop.system.*

@Canonical
class CmdComponent extends Component {

	String    eval
	Set<Stub> exports
	Set<Stub> imports

	CmdComponent(String name, Set<Declaration> declarations, String eval, Set<Stub> imports, Set<Stub> exports) {
		super(name, null, declarations, [], [])
		this.eval    = eval
		this.imports = imports
		this.exports = exports
	}
	CmdComponent(String name) {
		this(name, [] as Set, null, [] as Set, [] as Set)
	}

	void addDecl(Declaration d) { declarations << d }
	void addCons(Constraint c) { ErrorManager.error(ErrorId.CMD_CONSTRAINT) }
	void addRule(Rule r) {
		if (!r.isDirective) {
			super.addRule(r)
			return
		}

		def d = r.getDirective()
		switch (d.name) {
			case "lang:cmd:EVAL"  :
					   if (eval != null) ErrorManager.error(ErrorId.CMD_EVAL, name)
					   eval = (d.constant.value as String).replaceAll('^\"|\"$', ""); break
			case "lang:cmd:export":
					   exports << new Stub(d.backtick.name, "@past"); break
			case "lang:cmd:import":
					   imports << new Stub(d.backtick.name); break
			default               :
					   ErrorManager.error(ErrorId.CMD_DIRECTIVE, name)
		}
	}
	void addAll(Component other) {
		throw new UnsupportedOperationException("`addAll` is not supported on a command block")
	}

	def <T> T accept(IVisitor<T> v) { v.visit(this) }
}
