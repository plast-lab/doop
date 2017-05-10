package org.clyze.deepdoop.actions

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.clyze.deepdoop.datalog.*
import org.clyze.deepdoop.datalog.clause.*
import org.clyze.deepdoop.datalog.component.*
import org.clyze.deepdoop.datalog.element.*
import org.clyze.deepdoop.datalog.element.atom.*
import org.clyze.deepdoop.datalog.expr.*
import org.clyze.deepdoop.system.*

class LBCodeGenVisitingActor extends PostOrderVisitor<String> implements IActor<String> {

	AtomCollectingActor      acActor
	Set<String>              globalAtoms
	Map<IVisitable, String>  codeMap

	Component                unhandledGlobal
	Set<String>              handledAtoms

	Path                     outDir
	File                     latestFile
	List<Result>             results

	boolean                  inDecl

	LBCodeGenVisitingActor(String outDirName) {
		// Implemented this way, because Java doesn't allow usage of "this"
		// keyword before all implicit/explicit calls to super/this have
		// returned
		super(null)
		actor        = this

		acActor      = new AtomCollectingActor()
		codeMap      = [:]

		handledAtoms = [] as Set

		results      = []
		outDir       = Paths.get(outDirName)
	}

	String visit(Program flatP) {
		// Transform program before visiting nodes
		def n = flatP.accept(new InitVisitingActor()) as Program
		return super.visit(n)
	}

	String exit(Program n, Map<IVisitable, String> m) {
		n.accept(new PostOrderVisitor<IVisitable>(acActor))
		globalAtoms = acActor.getDeclaringAtoms(n.globalComp).keySet()

		// Check that all used predicates have a declaration/definition
		Set<String> allDeclAtoms        = [] + globalAtoms
		Map<String, IAtom> allUsedAtoms = [:]
		n.comps.values().each{
			allDeclAtoms += acActor.getDeclaringAtoms(it).keySet()
			allUsedAtoms << acActor.getUsedAtoms(it)
		}
		allUsedAtoms.each{ usedAtomName, usedAtom ->
			if (usedAtom.stage == "@past") return

			if (!(usedAtomName in allDeclAtoms))
				ErrorManager.warn(ErrorId.NO_DECL, usedAtomName)
		}

		// Compute dependency graph for components (and global predicates)
		def graph = new DependencyGraph(n)

		unhandledGlobal  = new Component(n.globalComp)
		Set<DependencyGraph.Node> currSet = []
		graph.getLayers().each{ layer ->
			if (layer.any{ node -> node instanceof DependencyGraph.CmdNode }) {
				emit(n, m, currSet)
				emitCmd(n, m, layer)
				currSet = [] as Set
			}
			else
				currSet += layer
		}
		emit(n, m, currSet)

		return null
	}

	String exit(Constraint n, Map<IVisitable, String> m) {
		def res = "${m[n.head]} -> ${m[n.body]}."
		codeMap[n] = res
		return res
	}

	void enter(Declaration n) {
		inDecl = true
	}
	String exit(Declaration n, Map<IVisitable, String> m) {
		inDecl = false
		def typeStr = n.types.collect{ m[it] }.join(', ')
		def res = "${m[n.atom]} -> ${typeStr}."
		codeMap[n] = res
		return res
	}

	String exit(RefModeDeclaration n, Map<IVisitable, String> m) {
		def res = "${m[n.types[0]]}, ${m[n.atom]} -> ${m[n.types[1]]}."
		codeMap[n] = res
		return res
	}

	String exit(Rule n, Map<IVisitable, String> m) {
		def res = m[n.head] + (n.body != null ? " <- " + m[n.body] : "") + "."
		codeMap[n] = res
		return res
	}

	String exit(AggregationElement n, Map<IVisitable, String> m) {
		"agg<<${m[n.var]} = ${m[n.predicate]}>> ${m[n.body]}"
	}

	String exit(ComparisonElement n, Map<IVisitable, String> m) { m[n.expr] }

	String exit(GroupElement n, Map<IVisitable, String> m) { "(${m[n.element]})" }

	String exit(LogicalElement n, Map<IVisitable, String> m) {
		def delim = (n.type == LogicalElement.LogicType.AND ? ", " : "; ")
		return n.elements.collect { m[it] }.join(delim)
	}

	String exit(NegationElement n, Map<IVisitable, String> m) { "!${m[n.element]}" }

	String exit(Constructor n, Map<IVisitable, String> m) {
		def functionalStr = exit(n as Functional, m)
		def res
		if (inDecl) {
			def directive = new Directive("lang:constructor", new Stub(n.type.name))
			res = directive.accept(this) + ".\n" + functionalStr
		}
		else {
			def entityStr = exit(n.type as Entity, m)
			res = functionalStr + ", " + entityStr
		}
		codeMap[n] = res
		return res
	}

	String exit(Directive n, Map<IVisitable, String> m) {
		def middle = (n.backtick != null ? "`" + n.backtick.name : "")
		if (n.isPredicate)
			return "${n.name}($middle)"
		else
			return "${n.name}[$middle] = ${m[n.constant]}"
	}

	String exit(Entity n, Map<IVisitable, String> m) {
		def entityStr = exit(n as Predicate, m)
		def res
		if (inDecl) {
			def directive = new Directive("lang:entity", new Stub(n.name))
			res = directive.accept(this) + ".\n" + entityStr
		}
		else {
			res = entityStr
		}
		codeMap[n] = res
		return res
	}

	String exit(Functional n, Map<IVisitable, String> m) {
		def keyStr = n.keyExprs.collect { m[it] }.join(', ')
		def stage = (n.stage == null || n.stage == "@past" ? "" : n.stage)
		return n.name + stage + "[" + keyStr + "]" + (n.valueExpr != null ? " = " + m[n.valueExpr] : "")
	}

	String exit(Predicate n, Map<IVisitable, String> m) {
		def str = n.exprs.collect { m[it] }.join(', ')
		def stage = (n.stage == null || n.stage == "@past" ? "" : n.stage)
		return n.name + stage + "(" + str + ")"
	}

	String exit(Primitive n, Map<IVisitable, String> m) { "${n.name}(${m[n.var]})" }

	String exit(RefMode n, Map<IVisitable, String> m) {
		def stage = (n.stage == null || n.stage == "@past" ? "" : n.stage)
		return n.name + stage + "(" + m[n.entityVar] + ":" + m[n.valueExpr] + ")"
	}

	String exit(Stub n, Map<IVisitable, String> m) { "STUB<${n.name}>" }

	String exit(BinaryExpr n, Map<IVisitable, String> m) {
		"${m[n.left]} ${n.op} ${m[n.right]}"
	}

	String exit(ConstantExpr n, Map<IVisitable, String> m) { n.value.toString() }

	String exit(FunctionalHeadExpr n, Map<IVisitable, String> m) { m[n.functional] }

	String exit(GroupExpr n, Map<IVisitable, String> m) { "(${m[n.expr]})" }

	String exit(VariableExpr n, Map<IVisitable, String> m) { n.name }


	void emit(Program n, Map<IVisitable, String> m, Set<DependencyGraph.Node> nodes) {
		latestFile = create("out_", ".logic")
		results << new Result(Result.Kind.LOGIC, latestFile)

		Set<String> currSet = []
		nodes.each { node ->
			if (node instanceof DependencyGraph.CompNode) {
				def c = n.comps[node.name]
				List<String> l = []
				c.declarations.each { l << codeMap[it] }
				c.constraints.each { l << codeMap[it] }
				c.rules.each { l << codeMap[it] }
				write(latestFile, l)
			} else if (node instanceof DependencyGraph.CmdNode)
				assert false
			else /* if (node instanceof PredNode)*/ {
				handledAtoms << node.name
				currSet << node.name
			}
		}

		handle(unhandledGlobal.declarations, latestFile)
		handle(unhandledGlobal.constraints,  latestFile)
		handle(unhandledGlobal.rules,        latestFile)
	}

	void emitCmd(Program n, Map<IVisitable, String> m, Set<DependencyGraph.Node> nodes) {
		assert nodes.size() == 1
		nodes.each { node ->
			assert node instanceof DependencyGraph.CmdNode
			assert latestFile != null
			def c = n.comps[node.name] as CmdComponent

			// Write frame rules from previous components
			c.rules.each { write(latestFile, codeMap[it]) }

			latestFile = create("out_", "-export.logic")
			results << new Result(Result.Kind.EXPORT, latestFile)

			c.rules.each {
				assert it.head.elements.size() == 1
				def atom = it.head.elements.first() as IAtom
				emitFilePredicate(atom, null, latestFile)
			}

			//for (Stub export : c.exports)
			//	write(_bashFile, "bloxbatch -db DB -keepDerivedPreds -exportCsv "+export.name+" -exportDataDir . -exportDelimiter '\\t'");

			results << new Result(c.eval)

			latestFile = create("out_", "-import.logic")
			results << new Result(Result.Kind.IMPORT, latestFile)

			c.declarations.each {
				assert !(it instanceof RefModeDeclaration)
				def atom = acActor.getDeclaringAtoms(it).values().first() as IAtom
				emitFilePredicate(atom, it, latestFile)
			}
		}
	}

	void emitFilePredicate(IAtom atom, Declaration d, File file) {
		def atomName = atom.name
		def vars = VariableExpr.genTempVars(atom.arity)

		def head = atomName + "(" + vars.collect{ it.name }.join(', ') + ")"
		def body = (0..atom.arity-1).collect{ i ->
			(d != null ? d.types[i].name : "string") + "(" + vars[i].name + ")"
		}.join(', ')
		def decl = "_$head -> $body."
		def rule = (d != null) ? "+$head <- _$head." : "+_$head <- $head."

		write(file, [
			"lang:physical:storageModel[`_$atomName] = \"DelimitedFile\".",
			"lang:physical:filePath[`_$atomName] = \"${atomName}.facts\".",
			"lang:physical:delimiter[`$atomName] = \"\\t\".",
			"lang:physical:hasColumnNames[`_$atomName] = false.",
			decl,
			rule
		])
	}


	File create(String prefix, String suffix) {
		Files.createTempFile(outDir, prefix, suffix).toFile()
	}
	void write(File file, String data) {
		file << data << "\n"
	}
	void write(File file, List<String> data) {
		data.each { write file, it }
	}

	def <T extends IVisitable> void handle(Set<T> set, File file) {
		Set<T> toRemove = []
		set.each {
			if (allHandledFor(it)) {
				write(file, codeMap[it])
				toRemove << it
			}
		}
		toRemove.each { set.remove(it) }
	}
	boolean allHandledFor(IVisitable n) {
		Set<String> atoms = []
		acActor.getDeclaringAtoms(n).values().each { atoms << it.name }
		acActor.getUsedAtoms(n).values().each { atoms << it.name }
		atoms.retainAll(globalAtoms)

		return atoms.every{ handledAtoms.contains(it) }
	}


	void enter(Program n) {}

	void enter(CmdComponent n) {}
	String exit(CmdComponent n, Map<IVisitable, String> m) { null }
	void enter(Component n) {}
	String exit(Component n, Map<IVisitable, String> m) { null }

	void enter(Constraint n) {}
	void enter(RefModeDeclaration n) {}
	void enter(Rule n) {}

	void enter(AggregationElement n) {}
	void enter(ComparisonElement n) {}
	void enter(GroupElement n) {}
	void enter(LogicalElement n) {}
	void enter(NegationElement n) {}

	void enter(Constructor n) {}
	void enter(Directive n) {}
	void enter(Entity n) {}
	void enter(Functional n) {}
	void enter(Predicate n) {}
	void enter(Primitive n) {}
	void enter(RefMode n) {}
	void enter(Stub n) {}

	void enter(BinaryExpr n) {}
	void enter(ConstantExpr n) {}
	void enter(FunctionalHeadExpr n) {}
	void enter(GroupExpr n) {}
	void enter(VariableExpr n) {}
}
