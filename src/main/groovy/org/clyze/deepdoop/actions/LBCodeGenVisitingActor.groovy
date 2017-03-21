package org.clyze.deepdoop.actions

import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import org.clyze.deepdoop.datalog.*
import org.clyze.deepdoop.datalog.clause.*
import org.clyze.deepdoop.datalog.component.*
import org.clyze.deepdoop.datalog.element.*
import org.clyze.deepdoop.datalog.element.atom.*
import org.clyze.deepdoop.datalog.expr.*
import org.clyze.deepdoop.system.*

class LBCodeGenVisitingActor extends PostOrderVisitor<String> implements IActor<String> {

	AtomCollectingActor      _acActor
	Set<String>              _globalAtoms
	Map<IVisitable, String>  _codeMap

	Component                _unhandledGlobal
	Set<String>              _handledAtoms

	Path                     _outDir
	Path                     _latestFile
	List<Result>             _results

	LBCodeGenVisitingActor(String outDirName) {
		// Implemented this way, because Java doesn't allow usage of "this"
		// keyword before all implicit/explicit calls to super/this have
		// returned
		super(null)
		_actor        = this

		_acActor      = new AtomCollectingActor()
		_codeMap      = [:]

		_handledAtoms = [] as Set

		_results      = []
		_outDir       = Paths.get(outDirName)
	}

	List<Result> getResults() {
		return _results
	}


	@Override
	String visit(Program flatP) {
		// Transform program before visiting nodes
		def n = flatP.accept(new InitVisitingActor()) as Program
		return super.visit(n)
	}

	@Override
	String exit(Program n, Map<IVisitable, String> m) {
		n.accept(new PostOrderVisitor<IVisitable>(_acActor))
		_globalAtoms = _acActor.getDeclaringAtoms(n.globalComp).keySet()

		// Check that all used predicates have a declaration/definition
		Set<String> allDeclAtoms        = _globalAtoms.collect() as Set
		Map<String, IAtom> allUsedAtoms = [:]
		n.comps.values().each{
			allDeclAtoms.addAll(_acActor.getDeclaringAtoms(it).keySet())
			allUsedAtoms.putAll(_acActor.getUsedAtoms(it))
		}
		allUsedAtoms.each{ usedAtomName, usedAtom ->
			if (usedAtom.stage() == "@past") return

			if (!allDeclAtoms.contains(usedAtomName))
				ErrorManager.warn(ErrorId.NO_DECL, usedAtomName)
		}


		// Compute dependency graph for components (and global predicates)
		def graph = new DependencyGraph(n)

		_unhandledGlobal  = new Component(n.globalComp)
		Set<DependencyGraph.Node> currSet = [] as Set
		graph.getLayers().each{ layer ->
			if (layer.any{ node -> node instanceof DependencyGraph.CmdNode }) {
				emit(n, m, currSet)
				emitCmd(n, m, layer)
				currSet = [] as Set
			}
			else
				currSet.addAll(layer)
		}
		emit(n, m, currSet)

		return null
	}

	@Override
	String exit(Constraint n, Map<IVisitable, String> m) {
		def res = m.get(n.head) + " -> " + m.get(n.body) + "."
		_codeMap[n] = res
		return res
	}
	@Override
	String exit(Declaration n, Map<IVisitable, String> m) {
		def typeStr = n.types.collect{ m[it] }.join(', ')
		def res = m[n.atom] + " -> " + typeStr + "."
		_codeMap[n] = res
		return res
	}
	@Override
	String exit(RefModeDeclaration n, Map<IVisitable, String> m) {
		def res = m[n.types.get(0)] + ", " + m[n.atom] + " -> " + m[n.types.get(1)] + "."
		_codeMap[n] = res
		return res
	}
	@Override
	String exit(Rule n, Map<IVisitable, String> m) {
		def res = m[n.head] + (n.body != null ? " <- " + m[n.body] : "") + "."
		_codeMap[n] = res
		return res
	}

	@Override
	String exit(AggregationElement n, Map<IVisitable, String> m) {
		return "agg<<" + m[n.var] + " = " + m[n.predicate] + ">> " + m[n.body]
	}
	@Override
	String exit(ComparisonElement n, Map<IVisitable, String> m) {
		return m[n.expr]
	}
	@Override
	String exit(GroupElement n, Map<IVisitable, String> m) {
		return "(" + m[n.element] + ")"
	}
	@Override
	String exit(LogicalElement n, Map<IVisitable, String> m) {
		def delim = (n.type == LogicalElement.LogicType.AND ? ", " : "; ")
		return n.elements.collect { m[it] }.join(delim)
	}
	@Override
	String exit(NegationElement n, Map<IVisitable, String> m) {
		return "!" + m[n.element]
	}

	@Override
	String exit(Directive n, Map<IVisitable, String> m) {
		def middle = (n.backtick != null ? "`" + n.backtick.name : "")
		if (n.isPredicate)
			return n.name + "(" + middle + ")"
		else
			return n.name + "[" + middle + "] = " + m[n.constant]
	}
	@Override
	String exit(Functional n, Map<IVisitable, String> m) {
		def keyStr = n.keyExprs.collect { m[it] }.join(', ')
		def stage = (n.stage == null || n.stage == "@past" ? "" : n.stage)
		return n.name + stage + "[" + keyStr + "]" + (n.valueExpr != null ? " = " + m[n.valueExpr] : "")
	}
	@Override
	String exit(Predicate n, Map<IVisitable, String> m) {
		def str = n.exprs.collect { m[it] }.join(', ')
		def stage = (n.stage == null || n.stage == "@past" ? "" : n.stage)
		return n.name + stage + "(" + str + ")"
	}
	@Override
	String exit(Entity n, Map<IVisitable, String> m) {
		return exit(n as Predicate, m)
	}
	@Override
	String exit(Primitive n, Map<IVisitable, String> m) {
		return n.name + "(" + m[n.var] + ")"
	}
	@Override
	String exit(RefMode n, Map<IVisitable, String> m) {
		def stage = (n.stage == null || n.stage == "@past" ? "" : n.stage)
		return n.name + stage + "(" + m[n.entityVar] + ":" + m[n.valueExpr] + ")"
	}
	@Override
	String exit(StubAtom n, Map<IVisitable, String> m) {
		return "STUB<" + n.name + ">"
	}

	@Override
	String exit(BinaryExpr n, Map<IVisitable, String> m) {
		return m[n.left] + " " + n.op + " " + m[n.right]
	}
	@Override
	String exit(ConstantExpr n, Map<IVisitable, String> m) {
		return n.value.toString()
	}
	@Override
	String exit(FunctionalHeadExpr n, Map<IVisitable, String> m) {
		return m[n.functional]
	}
	@Override
	String exit(GroupExpr n, Map<IVisitable, String> m) {
		return "(" + m[n.expr] + ")"
	}
	@Override
	String exit(VariableExpr n, Map<IVisitable, String> m) {
		return n.name
	}


	void emit(Program n, Map<IVisitable, String> m, Set<DependencyGraph.Node> nodes) {
		_latestFile = create("out_", ".logic")
		_results.add(new Result(Result.Kind.LOGIC, _latestFile))

		Set<String> currSet = [] as Set
		nodes.each { node ->
			if (node instanceof DependencyGraph.CompNode) {
				def c = n.comps[node.name]
				List<String> l = []
				c.declarations.each { l.add(_codeMap[it]) }
				c.constraints.each { l.add(_codeMap[it]) }
				c.rules.each { l.add(_codeMap[it]) }
				write(_latestFile, l)
			} else if (node instanceof DependencyGraph.CmdNode)
				assert false
			else /* if (node instanceof PredNode)*/ {
				_handledAtoms.add(node.name)
				currSet.add(node.name)
			}
		}

		handle(_unhandledGlobal.declarations, _latestFile)
		handle(_unhandledGlobal.constraints,  _latestFile)
		handle(_unhandledGlobal.rules,        _latestFile)
	}

	void emitCmd(Program n, Map<IVisitable, String> m, Set<DependencyGraph.Node> nodes) {
		assert nodes.size() == 1
		nodes.each { node ->
			assert node instanceof DependencyGraph.CmdNode
			assert _latestFile != null
			def c = n.comps[node.name] as CmdComponent

			// Write frame rules from previous components
			c.rules.each { write(_latestFile, _codeMap[it]) }

			_latestFile = create("out_", "-export.logic")
			_results.add(new Result(Result.Kind.EXPORT, _latestFile))

			c.rules.each {
				assert it.head.elements.size() == 1
				def atom = it.head.elements.first() as IAtom
				emitFilePredicate(atom, null, _latestFile)
			}

			//for (StubAtom export : c.exports)
			//	write(_bashFile, "bloxbatch -db DB -keepDerivedPreds -exportCsv "+export.name()+" -exportDataDir . -exportDelimiter '\\t'");

			_results.add(new Result(c.eval))

			_latestFile = create("out_", "-import.logic")
			_results.add(new Result(Result.Kind.IMPORT, _latestFile))

			c.declarations.each {
				assert !(it instanceof RefModeDeclaration)
				def atom = _acActor.getDeclaringAtoms(it).values().first() as IAtom
				emitFilePredicate(atom, d, _latestFile)
			}
		}
	}

	void emitFilePredicate(IAtom atom, Declaration d, Path file) {
		def atomName = atom.name()
		def vars = VariableExpr.genTempVars(atom.arity())

		def head = atomName + "(" + vars.collect{ it.name }.join(', ') + ")"
		def body = (0..atom.arity()-1).collect{ i ->
			(d != null ? d.types[i].name() : "string") + "(" + vars[i].name + ")"
		}.join(', ')
		def decl = "_$head -> $body."
		def rule = (d != null) ? "+$head <- _$head." : "+_$head <- $head."

		write(file, [
			"lang:physical:storageModel[`_$atomName] = \"DelimitedFile\".",
			"lang:physical:filePath[`_$atomName] = \"$atomName.facts\".",
			"lang:physical:delimiter[`$atomName] = \"\\t\".",
			"lang:physical:hasColumnNames[`_$atomName] = false.",
			decl,
			rule
		])
	}


	Path create(String prefix, String suffix) {
		try {
			return Files.createTempFile(_outDir, prefix, suffix)
		}
		catch (IOException e) {
			// TODO
		}
		return null
	}
	void write(Path file, String data) {
		write(file, Arrays.asList(data))
	}
	void write(Path file, List<String> data) {
		try {
			Files.write(file, data, Charset.forName("UTF-8"), StandardOpenOption.APPEND)
		}
		catch (IOException e) {
			// TODO
		}
	}

	def <T extends IVisitable> void handle(Set<T> set, Path file) {
		Set<T> toRemove = [] as Set
		set.each {
			if (allHandledFor(it)) {
				write(file, _codeMap[it])
				toRemove.add(it)
			}
		}
		toRemove.each { set.remove(it) }
	}
	boolean allHandledFor(IVisitable n) {
		Set<String> atoms = [] as Set
		_acActor.getDeclaringAtoms(n).values().each { atoms.add(it.name()) }
		_acActor.getUsedAtoms(n).values().each { atoms.add(it.name()) }
		atoms.retainAll(_globalAtoms)

		return atoms.every{ _handledAtoms.contains(it) }
	}

	void enter(Program n) {}

	void enter(CmdComponent n) {}
	String exit(CmdComponent n, Map<IVisitable, String> m) { return null }
	void enter(Component n) {}
	String exit(Component n, Map<IVisitable, String> m) { return null }

	void enter(Constraint n) {}
	void enter(Declaration n) {}
	void enter(RefModeDeclaration n) {}
	void enter(Rule n) {}

	void enter(AggregationElement n) {}
	void enter(ComparisonElement n) {}
	void enter(GroupElement n) {}
	void enter(LogicalElement n) {}
	void enter(NegationElement n) {}

	void enter(Directive n) {}
	void enter(Functional n) {}
	void enter(Predicate n) {}
	void enter(Entity n) {}
	void enter(Primitive n) {}
	void enter(RefMode n) {}
	void enter(StubAtom n) {}

	void enter(BinaryExpr n) {}
	void enter(ConstantExpr n) {}
	void enter(FunctionalHeadExpr n) {}
	void enter(GroupExpr n) {}
	void enter(VariableExpr n) {}
}
