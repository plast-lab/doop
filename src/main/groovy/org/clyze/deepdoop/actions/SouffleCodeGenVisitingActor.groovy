package org.clyze.deepdoop.actions

import groovy.transform.InheritConstructors
import org.clyze.deepdoop.datalog.Program
import org.clyze.deepdoop.datalog.clause.Declaration
import org.clyze.deepdoop.datalog.clause.Rule
import org.clyze.deepdoop.datalog.component.Component
import org.clyze.deepdoop.datalog.element.ComparisonElement
import org.clyze.deepdoop.datalog.element.GroupElement
import org.clyze.deepdoop.datalog.element.LogicalElement
import org.clyze.deepdoop.datalog.element.NegationElement
import org.clyze.deepdoop.datalog.element.atom.*
import org.clyze.deepdoop.datalog.expr.BinaryExpr
import org.clyze.deepdoop.datalog.expr.ConstantExpr
import org.clyze.deepdoop.datalog.expr.GroupExpr
import org.clyze.deepdoop.datalog.expr.IExpr
import org.clyze.deepdoop.datalog.expr.VariableExpr
import org.clyze.deepdoop.system.*

import static org.clyze.deepdoop.datalog.Annotation.Kind.INPUT
import static org.clyze.deepdoop.datalog.Annotation.Kind.OUTPUT
import static org.clyze.deepdoop.datalog.element.LogicalElement.LogicType.AND

@InheritConstructors
class SouffleCodeGenVisitingActor extends DefaultCodeGenVisitingActor {

	File currentFile
	TypeInferenceVisitingActor inferenceActor

	static class Extra {
		// Unbound variable in a rule's head that a constructor will bound eventually
		Map<IExpr, Constructor> unboundVar = [:]
		// Atom (predicate, functional or constructor) to full predicate representation
		Map<IAtom, String> atomToFull = [:]
		// Full predicate to partial predicate mapping
		Map<String, String> fullToPartial = [:]
		// Predicate depends on unbound vars to take values
		Map<IAtom, List<IExpr>> unboundVarsForAtom = [:]
		// Atom (predicate or functional) to list of variables
		Map<IAtom, List<IExpr>> varsForAtom = [:]
	}
	Extra extra

	String visit(Program p) {
		currentFile = createUniqueFile("out_", ".dl")
		results << new Result(Result.Kind.LOGIC, currentFile)

		inferenceActor = new TypeInferenceVisitingActor(infoActor)

		// Transform program before visiting nodes
		def n = p.accept(new NormalizeVisitingActor())
				.accept(new InitVisitingActor())
				.accept(infoActor)
				.accept(new ValidationVisitingActor(infoActor))
				.accept(inferenceActor)

		return super.visit(n as Program)
	}

	void enter(Component n) {
		inferenceActor.inferredTypes.each { predName, types ->
			def params = types.withIndex().collect { type, i -> "x$i:${mapType(type)}" }.join(", ")
			emit ".decl ${mini(predName)}($params)"
		}
		emit "/////////////////////"
		// Special rules to propagate info to supertypes
		infoActor.directSuperType.each { emit "${mini(it.value)}(x) :- ${mini(it.key)}(x)." }
		emit "/////////////////////"
	}

	String exit(Declaration n, Map<IVisitable, String> m) {
		if (n.annotations.any { it.kind == INPUT })
			emit ".input ${mini(n.atom.name)}"
		if (n.annotations.any { it.kind == OUTPUT })
			emit ".output ${mini(n.atom.name)}"
		return null
	}

	void enter(Rule n) {
		extra = new Extra()
		extra.unboundVar = n.head.elements
				.findAll { it instanceof Constructor }
				.collect { it as Constructor }
				.collectEntries { [(it.valueExpr) : it] }
	}

	String exit(Rule n, Map<IVisitable, String> m) {
		// Potentially a rule for a partial predicate
		emit "${m[n.head]} :- ${m[n.body]}."
		// Rules for populating full predicates from partial ones
		extra.unboundVarsForAtom.each { atom, vars ->
			def atomVars = extra.varsForAtom[atom].collect { (it as VariableExpr).name }
			def fullPred = extra.atomToFull[atom]
			def partialPred = extra.fullToPartial[fullPred]
			def body = ((partialPred ? [partialPred] : []) +
					vars.collect {
						def con = extra.unboundVar[it]
						def params = (con.keyExprs + [con.valueExpr])
								.collect { (it as VariableExpr).name }
								.collect { it in atomVars ? it : "_" }
								.join(", ")
						return "${mini(con.name)}($params)"
					}).join(", ")
			emit "$fullPred :- $body."
		}
		extra.unboundVar.values().each { con ->
			def fullPred = extra.atomToFull[con]
			def partialPred = extra.fullToPartial[fullPred]
			emit "$fullPred :- $partialPred."
		}
		extra = null
	}

	String exit(ComparisonElement n, Map<IVisitable, String> m) { m[n.expr] }

	String exit(GroupElement n, Map<IVisitable, String> m) { "(${m[n.element]})" }

	String exit(LogicalElement n, Map<IVisitable, String> m) {
		return n.elements.findAll { m[it] }.collect { m[it] }.join(n.type == AND ? ", " : "; ")
	}

	String exit(NegationElement n, Map<IVisitable, String> m) { "!${m[n.element]}" }

	String exit(Constructor n, Map<IVisitable, String> m) {
		if (!extra) return null

		def allParams = (n.keyExprs.collect { m[it] } + ['$']).join(", ")
		def fullPred = "${mini(n.name)}($allParams)"
		def boundParams = n.keyExprs.collect { m[it] }.join(", ")
		def partialPred = "${mini(n.name)}__pArTiAl($boundParams)"
		extra.atomToFull[n] = fullPred
		extra.fullToPartial[fullPred] = partialPred

		return partialPred
	}

	String exit(Functional n, Map<IVisitable, String> m) {
		// TODO
		def params = (n.keyExprs + [n.valueExpr]).collect { m[it] }.join(", ")
		return "${mini(n.name)}($params)"
	}

	String exit(Predicate n, Map<IVisitable, String> m) {
		def allParams = n.exprs.collect { m[it] }.join(", ")
		def fullPred = "${mini(n.name)}($allParams)"
		extra.atomToFull[n] = fullPred
		extra.varsForAtom[n] = n.exprs

		def unboundVars = n.exprs.findAll { extra.unboundVar[it] }

		if (unboundVars) {
			extra.unboundVarsForAtom[n] = unboundVars
			def boundParams = n.exprs.findAll { !(it in unboundVars) }.collect { m[it] }.join(", ")
			def partialPred = boundParams ? "${mini(n.name)}__pArTiAl($boundParams)" : null
			extra.fullToPartial[fullPred] = partialPred
			return partialPred
		} else
			return fullPred
	}

	String exit(BinaryExpr n, Map<IVisitable, String> m) { "${m[n.left]} ${n.op} ${m[n.right]}" }

	String exit(ConstantExpr n, Map<IVisitable, String> m) { n.value as String }

	String exit(GroupExpr n, Map<IVisitable, String> m) { "(${m[n.expr]})" }

	String exit(VariableExpr n, Map<IVisitable, String> m) { n.name }

	def emit(def data) { write currentFile, data }

	static def mini(def name) { name.replace ":", "_" }

	// TODO
	static def mapType(def name) {
		if (name == "string") return "symbol"
		//else if (name == "int") return "number"
		else return "number" // it will be constructed
	}
}
