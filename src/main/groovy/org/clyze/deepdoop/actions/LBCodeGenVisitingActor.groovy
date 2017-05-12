package org.clyze.deepdoop.actions

import groovy.transform.InheritConstructors
import org.clyze.deepdoop.datalog.*
import org.clyze.deepdoop.datalog.clause.*
import org.clyze.deepdoop.datalog.component.*
import org.clyze.deepdoop.datalog.element.*
import org.clyze.deepdoop.datalog.element.atom.*
import org.clyze.deepdoop.datalog.expr.*
import org.clyze.deepdoop.system.*

@InheritConstructors
class LBCodeGenVisitingActor extends DefaultCodeGenVisitingActor {

	Set<String>              globalAtoms

	Component                unhandledGlobal
	Set<String>              handledAtoms = [] as Set

	File                     latestFile

	boolean                  inDecl

	String visit(Program p) {
		// Transform program before visiting nodes
		def flatP = p.accept(new NormalizeVisitingActor(p.comps)) as Program
		def n = flatP.accept(new InitVisitingActor()) as Program
		return super.visit(n)
	}

	String exit(Program n, Map<IVisitable, String> m) {
		n.accept(new PostOrderVisitor<IVisitable>(acActor))
		globalAtoms = acActor.getDeclaringAtoms(n.globalComp).keySet()

		// Check that all used predicates have a declaration/definition
		def allDeclAtoms = globalAtoms
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
		def currSet = [] as Set
		graph.getLayers().each{ layer ->
			if (layer.any{ it instanceof DependencyGraph.CmdNode }) {
				emit(n, m, currSet)
				emitCmd(n, m, layer)
				currSet = [] as Set
			}
			else
				currSet << layer
		}
		emit(n, m, currSet)

		return null
	}

	String exit(Constraint n, Map<IVisitable, String> m) {
		"${m[n.head]} -> ${m[n.body]}."
	}

	void enter(Declaration n) { inDecl = true }

	String exit(Declaration n, Map<IVisitable, String> m) {
		inDecl = false
		def typeStr = n.types.collect{ m[it] }.join(', ')
		return "${m[n.atom]} -> ${typeStr}."
	}

	String exit(RefModeDeclaration n, Map<IVisitable, String> m) {
		"${m[n.types[0]]}, ${m[n.atom]} -> ${m[n.types[1]]}."
	}

	String exit(Rule n, Map<IVisitable, String> m) {
		m[n.head] + (n.body != null ? " <- " + m[n.body] : "") + "."
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
		if (inDecl) {
			def directive = new Directive("lang:constructor", new Stub(n.type.name))
			return directive.accept(this) + ".\n" + functionalStr
		}
		else {
			def entityStr = exit(n.type as Entity, m)
			return "$functionalStr, $entityStr"
		}
	}

	String exit(Directive n, Map<IVisitable, String> m) {
		def middle = (n.backtick ? "`" + n.backtick.name : "")
		if (n.isPredicate)
			return "${n.name}($middle)"
		else
			return "${n.name}[$middle] = ${m[n.constant]}"
	}

	String exit(Entity n, Map<IVisitable, String> m) {
		def entityStr = exit(n as Predicate, m)
		if (inDecl) {
			def directive = new Directive("lang:entity", new Stub(n.name))
			return directive.accept(this) + ".\n" + entityStr
		}
		else {
			return entityStr
		}
	}

	String exit(Functional n, Map<IVisitable, String> m) {
		def keyStr = n.keyExprs.collect { m[it] }.join(', ')
		def stage = ((n.stage == null || n.stage == "@past") ? "" : n.stage)
		def valueStr = (n.valueExpr ? " = " + m[n.valueExpr] : "")
		return "${n.name}$stage[$keyStr]$valueStr"
	}

	String exit(Predicate n, Map<IVisitable, String> m) {
		def str = n.exprs.collect { m[it] }.join(', ')
		def stage = ((n.stage == null || n.stage == "@past") ? "" : n.stage)
		return "${n.name}$stage($str)"
	}

	String exit(Primitive n, Map<IVisitable, String> m) { "${n.name}(${m[n.var]})" }

	String exit(RefMode n, Map<IVisitable, String> m) {
		def stage = ((n.stage == null || n.stage == "@past") ? "" : n.stage)
		return "${n.name}$stage(${m[n.entityVar]}:${m[n.valueExpr]})"
	}

	String exit(Stub n, Map<IVisitable, String> m) { throw UnsupportedOperationException() }


	String exit(BinaryExpr n, Map<IVisitable, String> m) { "${m[n.left]} ${n.op} ${m[n.right]}" }

	String exit(ConstantExpr n, Map<IVisitable, String> m) { n.value.toString() }

	String exit(FunctionalHeadExpr n, Map<IVisitable, String> m) { m[n.functional] }

	String exit(GroupExpr n, Map<IVisitable, String> m) { "(${m[n.expr]})" }

	String exit(VariableExpr n, Map<IVisitable, String> m) { n.name }


	void emit(Program n, Map<IVisitable, String> m, Set<DependencyGraph.Node> nodes) {
		latestFile = createUniqueFile("out_", ".logic")
		results << new Result(Result.Kind.LOGIC, latestFile)

		def currSet = [] as Set
		nodes.each { node ->
			if (node instanceof DependencyGraph.CompNode) {
				def c = n.comps[node.name]
				List<String> l = []
				c.declarations.each { l << m[it] }
				c.constraints.each { l << m[it] }
				c.rules.each { l << m[it] }
				write(latestFile, l)
			} else if (node instanceof DependencyGraph.CmdNode)
				assert false
			else /* if (node instanceof PredNode)*/ {
				handledAtoms << node.name
				currSet << node.name
			}
		}
		println handledAtoms

		handle(m, unhandledGlobal.declarations, latestFile)
		handle(m, unhandledGlobal.constraints,  latestFile)
		handle(m, unhandledGlobal.rules,        latestFile)
	}

	void emitCmd(Program n, Map<IVisitable, String> m, Set<DependencyGraph.Node> nodes) {
		assert nodes.size() == 1
		nodes.each { node ->
			assert node instanceof DependencyGraph.CmdNode
			assert latestFile != null
			def c = n.comps[node.name] as CmdComponent

			// Write frame rules from previous components
			c.rules.each { write(latestFile, m[it]) }

			latestFile = createUniqueFile("out_", "-export.logic")
			results << new Result(Result.Kind.EXPORT, latestFile)

			c.rules.each {
				assert it.head.elements.size() == 1
				def atom = it.head.elements.first() as IAtom
				emitFilePredicate(atom, null, latestFile)
			}

			//for (Stub export : c.exports)
			//	write(_bashFile, "bloxbatch -db DB -keepDerivedPreds -exportCsv "+export.name+" -exportDataDir . -exportDelimiter '\\t'");

			results << new Result(c.eval)

			latestFile = createUniqueFile("out_", "-import.logic")
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

	def <T extends IVisitable> void handle(Map<IVisitable, String> m, Set<T> set, File file) {
		Set<T> toRemove = []
		set.each {
			if (allHandledFor(it)) {
				write(file, m[it])
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
}
