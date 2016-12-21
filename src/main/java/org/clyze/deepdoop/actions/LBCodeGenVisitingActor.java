package org.clyze.deepdoop.actions;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import org.clyze.Helper;
import org.clyze.deepdoop.datalog.*;
import org.clyze.deepdoop.datalog.clause.*;
import org.clyze.deepdoop.datalog.component.*;
import org.clyze.deepdoop.datalog.component.DependencyGraph.*;
import org.clyze.deepdoop.datalog.element.*;
import org.clyze.deepdoop.datalog.element.atom.*;
import org.clyze.deepdoop.datalog.expr.*;
import org.clyze.deepdoop.system.*;

public class LBCodeGenVisitingActor extends PostOrderVisitor<String> implements IActor<String> {

	AtomCollectingActor     _acActor;
	Set<String>             _globalAtoms;
	Map<IVisitable, String> _codeMap;

	Component               _unhandledGlobal;
	Set<String>             _handledAtoms;

	Path                    _outDir;
	Path                    _latestFile;
	List<Result>            _results;

	public LBCodeGenVisitingActor(String outDirName) {
		// Implemented this way, because Java doesn't allow usage of "this"
		// keyword before all implicit/explicit calls to super/this have
		// returned
		super(null);
		_actor        = this;

		_acActor      = new AtomCollectingActor();
		_codeMap      = new HashMap<>();

		_handledAtoms = new HashSet<>();

		_results      = new ArrayList<>();
		_outDir      = Paths.get(outDirName);
	}
	public LBCodeGenVisitingActor() {
		this(".");
	}

	public List<Result> getResults() {
		return _results;
	}


	@Override
	public String visit(Program flatP) {
		// Transform program before visiting nodes
		PostOrderVisitor<IVisitable> v = new InitVisitingActor();
		Program n = (Program) flatP.accept(v);

		Map<IVisitable, String> m = new HashMap<>();
		m.put(n.globalComp, n.globalComp.accept(this));
		for (Component c : n.comps.values()) m.put(c, c.accept(this));
		return _actor.exit(n, m);
	}

	@Override
	public String exit(Program n, Map<IVisitable, String> m) {
		n.accept(new PostOrderVisitor<IVisitable>(_acActor));
		_globalAtoms = _acActor.getDeclaringAtoms(n.globalComp).keySet();

		// Check that all used predicates have a declaration/definition
		Map<String, Set<String>> reversePropagations = new HashMap<>();
		for (Propagation prop : n.props) {
			Set<String> fromSet = reversePropagations.get(prop.toId);
			if (fromSet == null) fromSet = new HashSet<>();
			fromSet.add(prop.fromId);
			reversePropagations.put(prop.toId, fromSet);
		}

		Set<String> allDeclAtoms = new HashSet<>(_globalAtoms);
		Set<String> allUsedAtoms = new HashSet<>();
		for (Component c : n.comps.values()) {
			allDeclAtoms.addAll(_acActor.getDeclaringAtoms(c).keySet());
			allUsedAtoms.addAll(_acActor.getUsedAtoms(c).keySet());
		}
		for (String usedPred : allUsedAtoms) {
			Set<String> potentialDeclPreds = InitVisitingActor.revert(usedPred, n.comps.keySet(), reversePropagations);
			boolean declFound = false;
			for (String potentialDeclPred : potentialDeclPreds) {
				if (allDeclAtoms.contains(potentialDeclPred)) {
					declFound = true;
					break;
				}
			}
			if (!declFound)
				ErrorManager.warn(this, ErrorId.NO_DECL, usedPred);
		}

		// Compute dependency graph for components (and global predicates)
		DependencyGraph graph = new DependencyGraph(n);

		_unhandledGlobal  = new Component(n.globalComp);
		Set<Node> currSet = new HashSet<>();;
		for (Set<Node> layer : graph.getLayers()) {
			boolean hasCmd = false;
			for (Node node : layer)
				if (node instanceof CmdNode) {
					hasCmd = true;
					break;
				}
			if (hasCmd) {
				emit(n, m, currSet);
				emitCmd(n, m, layer);
				currSet = new HashSet<>();
			}
			else
				currSet.addAll(layer);
		}
		emit(n, m, currSet);

		return null;
	}

	@Override
	public String exit(Constraint n, Map<IVisitable, String> m) {
		String res = m.get(n.head) + " -> " + m.get(n.body) + ".";
		_codeMap.put(n, res);
		return res;
	}
	@Override
	public String exit(Declaration n, Map<IVisitable, String> m) {
		StringJoiner joiner = new StringJoiner(", ");
		for (IAtom t : n.types) joiner.add(m.get(t));
		String res = m.get(n.atom) + " -> " + joiner + ".";
		_codeMap.put(n, res);
		return res;
	}
	@Override
	public String exit(RefModeDeclaration n, Map<IVisitable, String> m) {
		String res = m.get(n.types.get(0)) + ", " + m.get(n.atom) + " -> " + m.get(n.types.get(1)) + ".";
		_codeMap.put(n, res);
		return res;
	}
	@Override
	public String exit(Rule n, Map<IVisitable, String> m) {
		String res = m.get(n.head) + (n.body != null ? " <- " + m.get(n.body) : "") + ".";
		_codeMap.put(n, res);
		return res;
	}

	@Override
	public String exit(AggregationElement n, Map<IVisitable, String> m) {
		return "agg<<" + m.get(n.var) + " = " + m.get(n.predicate) + ">> " + m.get(n.body);
	}
	@Override
	public String exit(ComparisonElement n, Map<IVisitable, String> m) {
		return m.get(n.expr);
	}
	@Override
	public String exit(GroupElement n, Map<IVisitable, String> m) {
		return "(" + m.get(n.element) + ")";
	}
	@Override
	public String exit(LogicalElement n, Map<IVisitable, String> m) {
		StringJoiner joiner = new StringJoiner(n.type == LogicalElement.LogicType.AND ? ", " : "; ");
		for (IElement e : n.elements) joiner.add(m.get(e));
		return joiner.toString();
	}
	@Override
	public String exit(NegationElement n, Map<IVisitable, String> m) {
		return "!" + m.get(n.element);
	}

	@Override
	public String exit(Directive n, Map<IVisitable, String> m) {
		String middle = (n.backtick != null ? "`" + n.backtick.name : "");
		if (n.isPredicate)
			return n.name + "(" + middle + ")";
		else
			return n.name + "[" + middle + "] = " + m.get(n.constant);
	}
	@Override
	public String exit(Functional n, Map<IVisitable, String> m) {
		StringJoiner joiner = new StringJoiner(", ");
		for (IExpr e : n.keyExprs) joiner.add(m.get(e));
		return n.name + (n.stage == null ? "" : n.stage) + "[" + joiner + "]" + (n.valueExpr != null ? " = " + m.get(n.valueExpr) : "");
	}
	@Override
	public String exit(Predicate n, Map<IVisitable, String> m) {
		StringJoiner joiner = new StringJoiner(", ");
		for (IExpr e : n.exprs) joiner.add(m.get(e));
		return n.name + (n.stage == null ? "" : n.stage) + "(" + joiner + ")";
	}
	@Override
	public String exit(Primitive n, Map<IVisitable, String> m) {
		return n.name + "(" + m.get(n.var) + ")";
	}
	@Override
	public String exit(RefMode n, Map<IVisitable, String> m) {
		return n.name + (n.stage == null ? "" : n.stage) + "(" + m.get(n.entityVar) + ":" + m.get(n.valueExpr) + ")";
	}
	@Override
	public String exit(StubAtom n, Map<IVisitable, String> m) {
		return "STUB<" + n.name + ">";
	}

	@Override
	public String exit(BinaryExpr n, Map<IVisitable, String> m) {
		return m.get(n.left) + " " + n.op + " " + m.get(n.right);
	}
	@Override
	public String exit(ConstantExpr n, Map<IVisitable, String> m) {
		return n.value.toString();
	}
	@Override
	public String exit(FunctionalHeadExpr n, Map<IVisitable, String> m) {
		return m.get(n.functional);
	}
	@Override
	public String exit(GroupExpr n, Map<IVisitable, String> m) {
		return "(" + m.get(n.expr) + ")";
	}
	@Override
	public String exit(VariableExpr n, Map<IVisitable, String> m) {
		return n.name;
	}


	void emit(Program n, Map<IVisitable, String> m, Set<Node> nodes) {
		_latestFile = create("out_", ".logic");
		_results.add(new Result(Result.Kind.LOGIC, _latestFile));

		Set<String> currSet = new HashSet<>();
		for (Node node : nodes)
			if (node instanceof CompNode) {
				Component c = n.comps.get(node.name);
				List<String> l = new ArrayList<>();
				for (Declaration d : c.declarations) l.add(_codeMap.get(d));
				for (Constraint con: c.constraints)  l.add(_codeMap.get(con));
				for (Rule r : c.rules)               l.add(_codeMap.get(r));
				write(_latestFile, l);
			}
			else if (node instanceof CmdNode)
				assert false;
			else /* if (node instanceof PredNode)*/ {
				_handledAtoms.add(node.name);
				currSet.add(node.name);
			}

		handle(n.globalComp, _unhandledGlobal.declarations, _latestFile);
		handle(n.globalComp, _unhandledGlobal.constraints,  _latestFile);
		handle(n.globalComp, _unhandledGlobal.rules,        _latestFile);
	}

	void emitCmd(Program n, Map<IVisitable, String> m, Set<Node> nodes) {
		assert nodes.size() == 1;
		for (Node node : nodes) {
			assert node instanceof CmdNode;
			assert _latestFile != null;
			CmdComponent c = (CmdComponent) n.comps.get(node.name);

			// Write frame rules from previous components
			for (Rule r : c.rules)
				write(_latestFile, _codeMap.get(r));

			_latestFile = create("out_", "-export.logic");
			_results.add(new Result(Result.Kind.EXPORT, _latestFile));

			for (Rule r : c.rules) {
				assert r.head.elements.size() == 1;
				IAtom atom = (IAtom) r.head.elements.iterator().next();
				emitFilePredicate(atom, null, _latestFile);
			}

			//for (StubAtom export : c.exports)
			//	write(_bashFile, "bloxbatch -db DB -keepDerivedPreds -exportCsv "+export.name()+" -exportDataDir . -exportDelimiter '\\t'");

			_results.add(new Result(c.eval));

			_latestFile = create("out_", "-import.logic");
			_results.add(new Result(Result.Kind.IMPORT, _latestFile));

			for (Declaration d : c.declarations) {
				assert !(d instanceof RefModeDeclaration);
				IAtom atom = _acActor.getDeclaringAtoms(d).values().iterator().next();
				emitFilePredicate(atom, d, _latestFile);
			}
		}
	}

	void emitFilePredicate(IAtom atom, Declaration d, Path file) {
		String atomName = atom.name();

		List<VariableExpr> vars = new ArrayList<>(atom.arity());
		for (int i = 0 ; i < atom.arity() ; i++) vars.add(new VariableExpr("var" + i));

		String head = atomName + "(";
		StringJoiner joiner = new StringJoiner(", ");
		for (VariableExpr v : vars) joiner.add(v.name);
		head += joiner + ")";

		String decl = "_" + head + " -> ";
		joiner = new StringJoiner(", ");
		for (int i = 0 ; i < atom.arity() ; i++)
			joiner.add( (d != null ? d.types.get(i).name() : "string") + "(" + vars.get(i).name + ")");
		decl += joiner + ".";

		String rule = (d != null) ? "+"+head+" <- _"+head+"." : "+_"+head+" <- "+head+".";

		write(file, Arrays.asList(
			"lang:physical:storageModel[`_"+atomName+"] = \"DelimitedFile\".",
			"lang:physical:filePath[`_"+atomName+"] = \""+atomName+".facts\".",
			"lang:physical:delimiter[`"+atomName+"_] = \"\\t\".",
			"lang:physical:hasColumnNames[`_"+atomName+"] = false.",
			decl,
			rule
		));
	}


	Path create(String prefix, String suffix) {
		try {
			return Files.createTempFile(_outDir, prefix, suffix);
		}
		catch (IOException e) {
			// TODO
		}
		return null;
	}
	void write(Path file, String data) {
		write(file, Arrays.asList(data));
	}
	void write(Path file, List<String> data) {
		try {
			Files.write(file, data, Charset.forName("UTF-8"), StandardOpenOption.APPEND);
		}
		catch (IOException e) {
			// TODO
		}
	}
	<T extends IVisitable> void handle(Component c, Set<T> set, Path file) {
		Set<T> toRemove = new HashSet<>();
		for (T t : set) {
			if (allHandledFor(t)) {
				write(file, _codeMap.get(t));
				toRemove.add(t);
			}
		}
		for (T t : toRemove)
			set.remove(t);
	}
	boolean allHandledFor(IVisitable n) {
		Set<String> atoms = new HashSet<>();
		for (IAtom a : _acActor.getDeclaringAtoms(n).values()) atoms.add(a.name());
		for (IAtom a : _acActor.getUsedAtoms(n).values()) atoms.add(a.name());
		atoms.retainAll(_globalAtoms);

		for (String atom : atoms)
			if (!_handledAtoms.contains(atom))
				return false;
		return true;
	}
}
