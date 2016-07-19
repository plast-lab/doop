package deepdoop.datalog.component;

import deepdoop.actions.IVisitor;
import deepdoop.datalog.DeepDoopException;
import deepdoop.datalog.clause.*;
import deepdoop.datalog.element.atom.Directive;
import deepdoop.datalog.element.atom.StubAtom;
import java.util.HashSet;
import java.util.Set;

public class CmdComponent extends Component {

	public String              eval;
	public String              dir;
	public final Set<StubAtom> exports;
	public final Set<StubAtom> imports;

	public CmdComponent(String name, Set<Declaration> declarations, String eval, String dir, Set<StubAtom> imports, Set<StubAtom> exports) {
		super(name, null, declarations, new HashSet<>(), new HashSet<>());
		this.eval         = eval;
		this.dir          = dir;
		this.imports      = imports;
		this.exports      = exports;
	}
	public CmdComponent(String name) {
		this(name, new HashSet<>(), null, null, new HashSet<>(), new HashSet<>());
	}

	public String eval() { return eval; }
	public String dir()  { return dir; }

	@Override
	public void addDecl(Declaration d) {
		declarations.add(d);
	}
	@Override
	public void addCons(Constraint c) {
		throw new DeepDoopException("Constraints are not supported in a command block");
	}
	@Override
	public void addRule(Rule r) {
		// TODO must check for used defined rules but allow for frame rules
		//if (!r.isDirective)
		//	throw new DeepDoopException("Only directives are supported in a command block");
		if (!r.isDirective) {
			super.addRule(r);
			return;
		}

		Directive d = r.getDirective();
		switch (d.name) {
			case "lang:cmd:DIR"   :
					   if (dir != null) throw new DeepDoopException("DIR property is already specified in command block `" + this.name + "`");
					   dir = (String) d.constant.value; break;
			case "lang:cmd:EVAL"  :
					   if (eval != null) throw new DeepDoopException("EVAL property is already specified in command block `" + this.name + "`");
					   eval = (String) d.constant.value; break;
			case "lang:cmd:export":
					   exports.add(new StubAtom(d.backtick.name + ":past")); break;
			case "lang:cmd:import":
					   imports.add(new StubAtom(d.backtick.name)); break;
			default               :
					   throw new DeepDoopException("Invalid directive in command block `" + name + "`");
		}
	}
	@Override
	public void addAll(Component other) {
		throw new UnsupportedOperationException("`addAll` is not supported on a command block");
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		for (StubAtom p : exports)         builder.append(p + "\n");
		for (StubAtom p : imports)         builder.append(p + "\n");
		for (Declaration d : declarations) builder.append(d + "\n");
		return builder.toString();
	}


	@Override
	public <T> T accept(IVisitor<T> v) {
		return v.visit(this);
	}
}
