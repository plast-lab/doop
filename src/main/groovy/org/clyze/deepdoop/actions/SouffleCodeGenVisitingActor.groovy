package org.clyze.deepdoop.actions

import groovy.transform.InheritConstructors
import org.clyze.deepdoop.datalog.Program
import org.clyze.deepdoop.datalog.clause.Declaration
import org.clyze.deepdoop.datalog.clause.Rule
import org.clyze.deepdoop.datalog.element.ComparisonElement
import org.clyze.deepdoop.datalog.element.GroupElement
import org.clyze.deepdoop.datalog.element.LogicalElement
import org.clyze.deepdoop.datalog.element.NegationElement
import org.clyze.deepdoop.datalog.element.atom.*
import org.clyze.deepdoop.datalog.expr.BinaryExpr
import org.clyze.deepdoop.datalog.expr.ConstantExpr
import org.clyze.deepdoop.datalog.expr.GroupExpr
import org.clyze.deepdoop.datalog.expr.VariableExpr
import org.clyze.deepdoop.system.Result

import static org.clyze.deepdoop.datalog.Annotation.Kind.INPUT
import static org.clyze.deepdoop.datalog.Annotation.Kind.OUTPUT
import static org.clyze.deepdoop.datalog.element.LogicalElement.LogicType.AND

@InheritConstructors
class SouffleCodeGenVisitingActor extends DefaultCodeGenVisitingActor {

	File currentFile
	boolean inDecl
	boolean inRuleHead

	String visit(Program p) {
		currentFile = createUniqueFile("out_", ".dl")
		results << new Result(Result.Kind.LOGIC, currentFile)

		// Transform program before visiting nodes
		def n = p.accept(new NormalizeVisitingActor())
				.accept(new InitVisitingActor())
		n.accept(infoActor)
		n.accept(new ValidationVisitingActor(infoActor))

		return super.visit(n as Program)
	}

	void enter(Declaration n) { inDecl = true }

	String exit(Declaration n, Map<IVisitable, String> m) {
		inDecl = false
		if (n.atom instanceof Entity) {
			def entityName = n.atom.name
			def rest = (n.types.isEmpty() ? "" : " = ${n.types.first().name}")
			def params = "x:$entityName"
			emit ".type ${entityName}$rest"
			emit ".decl ${type(entityName, params)}"
		} else {
			def params = n.types.collect { m[it] }.join(", ")
			emit ".decl ${pred(n.atom.name, params)}"
		}

		if (n.annotations.any { it.kind == INPUT })
			emit ".input ${mini(n.atom.name)}"
		if (n.annotations.any { it.kind == OUTPUT })
			emit ".output ${mini(n.atom.name)}"

		return null
	}

	String visit(Rule n) {
		actor.enter(n)
		def m = [:]

		inRuleHead = true
		m[n.head] = n.head.accept(this)
		inRuleHead = false

		def constructors = n.head.elements.findAll { it instanceof Constructor }
		def newBody = n.body
		if (!newBody) newBody = new LogicalElement(AND, [])
		constructors.each { newBody.elements << it }
		m[n.body] = newBody.accept(this)
		return actor.exit(n, m)
	}

	String exit(Rule n, Map<IVisitable, String> m) {
		emit "${m[n.head]} :- ${m[n.body]}."
	}

	String exit(ComparisonElement n, Map<IVisitable, String> m) { m[n.expr] }

	String exit(GroupElement n, Map<IVisitable, String> m) { "(${m[n.element]})" }

	String exit(LogicalElement n, Map<IVisitable, String> m) {
		def delim = (n.type == AND ? ", " : "; ")
		return n.elements.collect { m[it] }.join(delim)
	}

	String exit(NegationElement n, Map<IVisitable, String> m) { "!${m[n.element]}" }

	String exit(Constructor n, Map<IVisitable, String> m) {
		def params = (n.keyExprs + [n.valueExpr]).collect { m[it] }.join(", ")

		if (inRuleHead)
			return "${type(n.entity.name, m[n.valueExpr])}, ${pred(n.name, params)}, ${pred("$n.name:HeadMacro", params)}"
		else if (!inDecl)
			return "${pred("$n.name:BodyMacro", params)}"
		else
			return null
	}

	String exit(Entity n, Map<IVisitable, String> m) {
		inDecl ? "${m[n.exprs.first()]}:${n.name}" : null
	}

	String exit(Functional n, Map<IVisitable, String> m) {
		def params = (n.keyExprs + [n.valueExpr]).collect { m[it] }.join(", ")
		return "${n.name}($params)"
	}

	String exit(Predicate n, Map<IVisitable, String> m) {
		def params = n.exprs.collect { m[it] }.join(", ")
		return "${n.name}($params)"
	}

	String exit(Primitive n, Map<IVisitable, String> m) {
		inDecl ? "${m[n.var]}:${mapPrimitive(n.name)}" : null
	}

	String exit(BinaryExpr n, Map<IVisitable, String> m) { "${m[n.left]} ${n.op} ${m[n.right]}" }

	String exit(ConstantExpr n, Map<IVisitable, String> m) { n.value as String }

	String exit(GroupExpr n, Map<IVisitable, String> m) { "(${m[n.expr]})" }

	String exit(VariableExpr n, Map<IVisitable, String> m) { n.name }

	def emit(def data) { write currentFile, data }

	static def pred(def name, def params) { "${mini(name)}($params)" }

	static def type(def name, def params) { "is${mini(name)}($params)" }

	static def mini(def name) { name.replace ":", "_" }

	static def mapPrimitive(def name) {
		if (name == "string") return "symbol"
		else if (name == "int") return "number"
		else return name
	}
}
