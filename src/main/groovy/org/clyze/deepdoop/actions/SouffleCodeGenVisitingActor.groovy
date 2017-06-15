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
import org.clyze.deepdoop.datalog.expr.VariableExpr
import org.clyze.deepdoop.system.*

import static org.clyze.deepdoop.datalog.Annotation.Kind.INPUT
import static org.clyze.deepdoop.datalog.Annotation.Kind.OUTPUT
import static org.clyze.deepdoop.datalog.element.LogicalElement.LogicType.AND

@InheritConstructors
class SouffleCodeGenVisitingActor extends DefaultCodeGenVisitingActor {

	File currentFile
	TypeInferenceVisitingActor inferenceActor
	Map<String, List<String>> typesStructure = [:]
	Map<String, String> originalTypeToGenType = [:]
	int lastGenTypeCounter
	List<String> pendingRecords
	boolean inRule = false

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

		inferTypeStructure()

		return super.visit(n as Program)
	}

	void enter(Component n) {
		inferenceActor.inferredTypes.each { predName, types ->
			def params = types.withIndex().collect { type, i -> "x$i:${mapType(type)}" }.join(", ")
			emit ".decl ${pred(predName, params)}"
		}
	}

	String exit(Declaration n, Map<IVisitable, String> m) {
		if (n.atom instanceof Entity) {
			def structure = typesStructure[n.atom.name]
			if (structure.size() == 1)
				emit ".decl ${pred(n.atom.name, "x:${structure.first()}")}"
			else {
				def params = structure.withIndex().collect { "x${it[1]}:${it[0]}" }.join(", ")
				def genType = "T${lastGenTypeCounter++}"
				originalTypeToGenType[n.atom.name] = genType
				emit ".type $genType = [$params]"
				emit ".decl ${pred(n.atom.name, "x:$genType")}"
			}
			//def entityName = n.atom.name
			//def rest = (n.types.isEmpty() ? "" : " = ${n.types.first().name}")
			//def params = "x:$entityName"
			//emit ".type ${entityName}$rest"
			//emit ".decl ${type(entityName, params)}"
		}// else {
			//def params = n.types.collect { m[it] }.join(", ")
			//emit ".decl ${pred(n.atom.name, params)}"
		//}

		if (n.annotations.any { it.kind == INPUT })
			emit ".input ${mini(n.atom.name)}"
		if (n.annotations.any { it.kind == OUTPUT })
			emit ".output ${mini(n.atom.name)}"

		return null
	}

	/*String visit(Rule n) {
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
	}*/
	void enter(Rule n) {
		pendingRecords = []
		inRule = true
	}

	String exit(Rule n, Map<IVisitable, String> m) {
		inRule = false
		def body = m[n.body] + (pendingRecords.isEmpty() ? "" : ", ${pendingRecords.join(", ")}")
		emit "${m[n.head]} :- ${body}."
	}

	String exit(ComparisonElement n, Map<IVisitable, String> m) { m[n.expr] }

	String exit(GroupElement n, Map<IVisitable, String> m) { "(${m[n.element]})" }

	String exit(LogicalElement n, Map<IVisitable, String> m) {
		return n.elements.collect { m[it] }.join(n.type == AND ? ", " : "; ")
	}

	String exit(NegationElement n, Map<IVisitable, String> m) { "!${m[n.element]}" }

	String exit(Constructor n, Map<IVisitable, String> m) {
		if (inRule) {
			def nilTypes = (typesStructure[infoActor.constructorBaseType[n.name]].size() - n.keyExprs.size())
			def record = n.keyExprs.collect { m[it] } + (0..<nilTypes).collect { "nil" }
			pendingRecords << "${m[n.valueExpr]} = [${record.join(", ")}]"
		}
		def params = (n.keyExprs + [n.valueExpr]).collect { m[it] }.join(", ")
		return "${pred(n.name, params)}, ${pred(n.entity.name, m[n.valueExpr])}"

		//if (inRuleHead)
			//return "${pred(n.entity.name, m[n.valueExpr])}, ${pred(n.name, params)}, ${pred("$n.name:HeadMacro", params)}"
		//else if (!inDecl)
		//	return "${pred("$n.name:BodyMacro", params)}"
		//else
		//	return null
	}

	String exit(Entity n, Map<IVisitable, String> m) {
		//inDecl ? "${m[n.exprs.first()]}:${n.name}" : null
		null
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
		//inDecl ? "${m[n.var]}:${mapPrimitive(n.name)}" : null
		null
	}

	String exit(BinaryExpr n, Map<IVisitable, String> m) { "${m[n.left]} ${n.op} ${m[n.right]}" }

	String exit(ConstantExpr n, Map<IVisitable, String> m) { n.value as String }

	String exit(GroupExpr n, Map<IVisitable, String> m) { "(${m[n.expr]})" }

	String exit(VariableExpr n, Map<IVisitable, String> m) { n.name }

	void inferTypeStructure() {
		// For each type that has constructors
		infoActor.constructorsPerType.each { type, constructors ->
			def maxElement = constructors.max { inferenceActor.inferredTypes[it].size() }
			// Decrease by one to ignore the entity at the end of the list
			def maxSize = inferenceActor.inferredTypes[maxElement].size() - 1

			def finalType = constructors
			// Collect types found in key positions
					.collect {
				def keyTypes = inferenceActor.inferredTypes[it].dropRight(1)
				def padLength = maxSize - keyTypes.size()
				keyTypes += (0..<padLength).collect { null }
				return keyTypes
			}
			// Transpose elements from each list
					.transpose()
			// Find a single type for each position
					.withIndex().collect { typesForIndex ->
				if (typesForIndex[0].every { !it || it == "string" }) return mapType("string")
				if (typesForIndex[0].every { !it || it == "int" }) return mapType("int")
				ErrorManager.error(ErrorId.INCOMPATIBLE_TYPES, type, typesForIndex[1])
				return null
			}

			if (finalType.size() != 1 && !originalTypeToGenType[type]) {
				def genType = "T${lastGenTypeCounter++}"
				originalTypeToGenType[type] = genType
			}
			typesStructure[type] = finalType
		}
	}

	def emit(def data) { write currentFile, data }

	static def pred(def name, def params) { "${mini(name)}($params)" }

	//static def type(def name, def params) { "is${mini(name)}($params)" }

	static def mini(def name) { name.replace ":", "_" }

	//static def mapPrimitive(def name) {
	//	if (name == "string") return "symbol"
	//	else if (name == "int") return "number"
	//	else return name
	//}

	def mapType(def name) {
		def genType = originalTypeToGenType[name]
		if (name == "string") return "symbol"
		else if (name == "int") return "number"
		else if (genType) return genType
		else return "symbol"
	}
}
