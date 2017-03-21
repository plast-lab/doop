package org.clyze.deepdoop.datalog.component

import org.clyze.deepdoop.actions.*
import org.clyze.deepdoop.datalog.clause.*
import org.clyze.deepdoop.datalog.element.atom.*
import org.clyze.deepdoop.system.*

class CmdComponent extends Component {

	public String              eval
	public final Set<StubAtom> exports
	public final Set<StubAtom> imports

	CmdComponent(String name, Set<Declaration> declarations, String eval, Set<StubAtom> imports, Set<StubAtom> exports) {
		super(name, null, declarations, [], [])
		this.eval    = eval
		this.imports = imports
		this.exports = exports
	}
	CmdComponent(String name) {
		this(name, [], null, [], [])
	}

	@Override
	void addDecl(Declaration d) { declarations.add(d) }
	@Override
	void addCons(Constraint c) { ErrorManager.error(ErrorId.CMD_CONSTRAINT) }
	@Override
	void addRule(Rule r) {
		if (!r.isDirective) {
			super.addRule(r)
			return
		}

		def d = r.getDirective()
		switch (d.name) {
			case "lang:cmd:EVAL"  :
					   if (eval != null) ErrorManager.error(ErrorId.CMD_EVAL, name)
					   eval = ((String) d.constant.value).replaceAll('^\"|\"$', ""); break
			case "lang:cmd:export":
					   exports.add(new StubAtom(d.backtick.name, "@past")); break
			case "lang:cmd:import":
					   imports.add(new StubAtom(d.backtick.name)); break
			default               :
					   ErrorManager.error(ErrorId.CMD_DIRECTIVE, name)
		}
	}
	@Override
	void addAll(Component other) {
		throw new UnsupportedOperationException("`addAll` is not supported on a command block")
	}
	@Override
	<T> T accept(IVisitor<T> v) { return v.visit(this) }
}
