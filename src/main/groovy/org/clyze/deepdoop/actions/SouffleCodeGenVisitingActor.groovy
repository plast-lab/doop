package org.clyze.deepdoop.actions

import groovy.transform.InheritConstructors
import org.clyze.deepdoop.datalog.*
import org.clyze.deepdoop.datalog.clause.*
import org.clyze.deepdoop.datalog.component.*
import org.clyze.deepdoop.datalog.element.*
import org.clyze.deepdoop.datalog.element.atom.*
import org.clyze.deepdoop.datalog.expr.*
import org.clyze.deepdoop.system.*
import static org.clyze.deepdoop.datalog.element.LogicalElement.LogicType.*

@InheritConstructors
class SouffleCodeGenVisitingActor extends DefaultCodeGenVisitingActor {

	File currentFile
	boolean inDecl
	boolean inRuleHead

	def cacheTypes   = [:]
	def pendingTypes = [:]

	String visit(Program p) {
		currentFile = createUniqueFile("out_", ".dl")
		results << new Result(Result.Kind.LOGIC, currentFile)

		// Transform program before visiting nodes
		def n = p.accept(new NormalizeVisitingActor())
				.accept(new InitVisitingActor())
		return super.visit(n as Program)
	}

	void enter(Component n) {
		n.declarations.each{
			def entity = it.atom.name
			if (it.atom instanceof Entity && !cacheTypes[entity]) {
				pendingTypes = [:]
				cacheTypes[entity] = inferConstructorTypes(n.constructionInfo[entity])
				pendingTypes.each { id, types ->
					emit ".type $id = ${types.join('|')}"
				}
			}
		}
	}

	void enter(Declaration n) { inDecl = true }

	String exit(Declaration n, Map<IVisitable, String> m) {
		inDecl = false
		if (n.atom instanceof Entity) {
			def entityName = n.atom.name
			def generalConstructor = cacheTypes[entityName]
			if (generalConstructor) {
				def types = generalConstructor.join(", ")
				emit ".type ${entityName} = [$types]"
			}
			else {
				def rest = (n.types.isEmpty() ? "" : " = ${n.types.first().name}")
				emit ".type ${entityName}$rest"
			}
			emit ".decl is${entityName}(?x:${entityName})"
		}
		else {
			def params = n.types.collect{ m[it] }.join(", ")
			emit ".decl ${n.atom.name}($params)"
		}
		return null
	}

	String visit(Rule n) {
		actor.enter(n)
		def m = [:]

		inRuleHead = true
		m[n.head] = n.head.accept(this)
		inRuleHead = false

		def constructors = n.head.elements.findAll{ it instanceof Constructor }
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
		if (inRuleHead) {
			def params = (n.keyExprs + [n.valueExpr]).collect{ m[it] }.join(", ")
			return "is${n.entity.name}(${m[n.valueExpr]}), $n.name($params)"
		}
		else if (!inDecl) {
			def generalConstructor = cacheTypes[n.entity.name]
			def params = n.keyExprs.collect{ m[it] }
			def rest = generalConstructor.size() - params.size()
			(0..<rest).each{ params << "nil" }
			if (params.size() == 1)
				return "${m[n.valueExpr]} = ${params.join(', ')}"
			else
				return "${m[n.valueExpr]} = [${params.join(', ')}]"
		}
		return null
	}

	String exit(Entity n, Map<IVisitable, String> m) {
		inDecl ? "${m[n.exprs.first()]}:${n.name}" : null
	}

	String exit(Functional n, Map<IVisitable, String> m) {
		def params = (n.keyExprs + [n.valueExpr]).collect{ m[it] }.join(", ")
		return "${n.name}($params)"
	}

	String exit(Predicate n, Map<IVisitable, String> m) {
		def params = n.exprs.collect{ m[it] }.join(", ")
		return "${n.name}($params)"
	}

	String exit(Primitive n, Map<IVisitable, String> m) {
		inDecl ? "${m[n.var]}:${mapPrimitive(n.name)}" : null
	}

	String exit(BinaryExpr n, Map<IVisitable, String> m) { "${m[n.left]} ${n.op} ${m[n.right]}" }

	String exit(ConstantExpr n, Map<IVisitable, String> m) { n.value as String }

	String exit(FunctionalHeadExpr n, Map<IVisitable, String> m) { m[n.functional] }

	String exit(GroupExpr n, Map<IVisitable, String> m) { "(${m[n.expr]})" }

	String exit(VariableExpr n, Map<IVisitable, String> m) { n.name }


	def emit(def data) { write currentFile, data }

	static def mapPrimitive(def name) {
		if (name == "string") return "symbol"
		else if (name == "int") return "number"
		else return name
	}

	static def minify(def name) {
		if (name == "symbol") return "S"
		else if (name == "number") return "N"
		else return name
	}

	def inferConstructorTypes(List<Tuple2<Declaration,Constructor>> constructionInfo) {
		if (!constructionInfo) return

		def maxLen = constructionInfo.max{ it.second.keyExprs.size() }.second.keyExprs.size()
		def types = []
		(0..<maxLen).each{ index ->
			def mergeTypes = [] as Set
			constructionInfo.each{
				def (Declaration declaration, Constructor constructor) = it
				if (index < constructor.keyExprs.size())
					mergeTypes << mapPrimitive(declaration.types.get(index).name)
			}

			if (mergeTypes.size() > 1) {
				def typeId = "T_"
				mergeTypes.each{ typeId += minify it }
				types << typeId
				pendingTypes[typeId] = mergeTypes
			}
			else
				types << mergeTypes.first()
		}
		return types
	}
}
