package deepdoop.datalog;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class CmdComponent extends Component {

	String           _dir;
	String           _cmd;
	Set<String>      _exports;
	Set<String>      _imports;
	Set<Declaration> _declarations;

	CmdComponent(String name, String dir, String cmd, Set<String> imports, Set<String> exports, Set<Declaration> declarations) {
		super(name);
		_dir          = dir;
		_cmd          = cmd;
		_exports      = exports;
		_imports      = imports;
		_declarations = declarations;
	}
	public CmdComponent(String name) {
		super(name);
		_exports      = new HashSet<>();
		_imports      = new HashSet<>();
		_declarations = new HashSet<>();
	}

	@Override
	public void addDecl(Declaration d) {
		_declarations.add(d);
	}
	@Override
	public void addCons(Constraint c) {
		throw new DeepDoopException("Constraints are not supported in a command block");
	}
	@Override
	public void addRule(Rule r) {
		if (!r.isDirective)
			throw new DeepDoopException("Only directives are supported in a command block");

		Directive d = r.getDirective();
		switch (d.name) {
			case "lang:cmd:DIR"   : _dir = (String) d.constant.value; break;
			case "lang:cmd:EVAL"  : _cmd = (String) d.constant.value; break;
			case "lang:cmd:export": _exports.add(d.backtick + ":past"); break;
			case "lang:cmd:import": _imports.add(d.backtick); break;
			default               : throw new DeepDoopException("Invalid directive in command block `" + name + "`");
		}
	}
	@Override
	public void addAll(Component other) {
		throw new UnsupportedOperationException("`addAll` is not supported on a command block");
	}

	@Override
	public CmdComponent flatten(Map<String, Component> allComps) { return this; }

	@Override
	public Map<String, IAtom> getAtoms() {
		Map<String, IAtom> map = new HashMap<>();
		for (String pred : _exports) map.put(pred, null);
		for (Declaration d : _declarations) map.putAll(d.getAtoms());
		return map;
	}

	@Override
	public Map<String, IAtom> getDeclaringAtoms() {
		Map<String, IAtom> map = new HashMap<>();
		for (Declaration d : _declarations) map.putAll(d.getDeclaringAtoms());
		return map;
	}

	@Override
	public CmdComponent init(Initializer ini) {

		Set<String> newExports = new HashSet<>();
		for (String pred : _exports) newExports.add(ini.name(pred));
		Set<String> newImports = new HashSet<>();
		for (String pred : _imports) newImports.add(ini.name(pred));
		Set<Declaration> newDeclarations = new HashSet<>();
		for (Declaration d : _declarations) newDeclarations.add(d.init(ini));
		return new CmdComponent(ini.id(), _dir, _cmd, newImports, newExports, newDeclarations);
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		for (String pred : _exports) builder.append(pred + "\n");
		for (String pred : _imports) builder.append(pred + "\n");
		for (Declaration d : _declarations) builder.append(d + "\n");
		return builder.toString();
	}
}
