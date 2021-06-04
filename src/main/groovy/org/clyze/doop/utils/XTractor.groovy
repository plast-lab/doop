package org.clyze.doop.utils

import groovy.transform.Canonical
import org.clyze.doop.core.DoopAnalysis

class XTractor {
	static DoopAnalysis analysis
	static File outFile

	static def arrayMeta = [:].withDefault { [] }
	static Map<String, Set<String>> varAliases = [:].withDefault { [] as Set }

	static void run(DoopAnalysis analysis) {
		this.analysis = analysis
		outFile = new File(analysis.database, "xtractor-out.dl")
		outFile.text = ""

		new File(analysis.database, "Flows_EXT.csv").eachLine {
			def (String from, String to) = it.split("\t")
			varAliases[from] << to
		}

		arrays()
//		conditions()
//		schema()

		println "Results in... $outFile"
	}

	static def arrays() {
		def arrayMetaFacts = []
		def mainArrays = []
		Map<String, Integer> relNameToVariant = [:]
		outFile << ".decl arr_META(relation:symbol, name:symbol, types:symbol, dimensions:number)\n"
		new File(analysis.database, "MainArrayVar.csv").eachLine {
			def (String array, String name, String types) = it.split("\t")
			def dimensions = types.count("[]")
			def relName = "${name.split("#").first()}" as String
			def variant = relNameToVariant[relName]
			if (variant == null)
				relNameToVariant[relName] = 0
			else {
				relNameToVariant[relName] = variant + 1
				relName += "_$variant"
			}
			def dims = (1..dimensions).collect { "i$it:number" }.join(", ")
			outFile << ".decl $relName($dims, value:symbol)\n"
			outFile << ".decl ${relName}_Init($dims, value:symbol)\n"
			outFile << ".decl ${relName}_UnknownPos(dim:number)\n"
			def dimSizes = (1..dimensions).collect { "dim$it:number" }.join(", ")
			outFile << ".decl ${relName}_DimSizes($dimSizes)\n"
			outFile << ".decl ${relName}_Provided($dims, value:symbol)\n"
			outFile << ".input ${relName}_Provided\n"
			outFile << ".decl ${relName}_Missing($dims, value:symbol)\n"
			arrayMetaFacts << "arr_META(\"$relName\", \"$array\", \"$types\", $dimensions)."
			def metaInfo = [relName, name, types, dimensions]
			varAliases[array].each { arrayMeta[it] = metaInfo }
			mainArrays << array
		}
		outFile << "\n"
		arrayMetaFacts.each { outFile << "$it\n" }

		outFile << "\n"
		def arrayDims = [:].withDefault { [:] }
		new File(analysis.database, "ArrayDims.csv").eachLine {
			def (String array, pos, size) = it.split("\t")
			arrayDims[array][pos as int] = size
		}
		mainArrays.each { array ->
			def (String relName, name, types, int dimensions) = arrayMeta[array]
			def sizes = arrayDims[array]
			def allSizes = (1..dimensions).collect { sizes[it-1] ?: -1 }
			outFile << "${relName}_DimSizes(${allSizes.join(", ")}).\n"
		}

		outFile << "\n"
		def load_from2index2to = [:].withDefault { [:].withDefault { [] } }
		new File(analysis.database, "ArrayLoad.csv").eachLine {
			def (String to, String from, index) = it.split("\t")
			load_from2index2to[from][index as int] << to
		}
		def store_to2index2value = [:].withDefault { [:] }
		new File(analysis.database, "ArrayStore.csv").eachLine {
			def (String to, index, value) = it.split("\t")
			store_to2index2value[to][index as int] = value
		}

		def fixVal = { String value, String types ->
			(types.startsWith("char") && value.isNumber()) ? "\"${value.toInteger() as char}\"" : value
		}

		def appendToIndices
		appendToIndices = { String array, List indices, String currVar ->
			def nextIndicesAndVars = load_from2index2to[currVar]
			if (nextIndicesAndVars.isEmpty()) {
				def (String relName, name, String types, int dimensions) = arrayMeta[array]
				store_to2index2value[currVar].each { lastIndex, String value ->
					value = fixVal(value, types)
					outFile << "$relName(${(indices + [lastIndex, value]).join(", ")}).\n"
				}
				return
			}
			nextIndicesAndVars.each { index, vars ->
				indices << index
				vars.each { appendToIndices(array, indices, it) }
				indices.removeLast()
			}
		}
		arrayMeta.keySet().each { appendToIndices(it, [], it) }

		outFile << "\n"
		new File(analysis.database, "ArrayInitialized.csv").eachLine {
			def (String array, String value) = it.split("\t")
			if (!value.isNumber()) throw new RuntimeException("Invalid AP?")
			def (String relName, name, String types, int dimensions) = arrayMeta[array]
			def allZero = (1..dimensions).collect { 0 }.join(", ")
			def indexes = (1..dimensions).collect {"i$it" }.join(", ")
			outFile << "${relName}_Init($allZero, ${fixVal(value, types)}).\n"
			outFile << "$relName($indexes, val) :- ${relName}_Init($indexes, val).\n"
			dimensions.times {index ->
				def headIndexes = (1..dimensions).collect {it-1 == index ? "i$it + 1" : "i$it" }.join(", ")
				def size = arrayDims[array][index] as int
				outFile << "${relName}_Init($headIndexes, val) :- ${relName}_Init($indexes, val), i${index+1} < ${size-1}.\n"
			}
		}

		outFile << "\n"
		def arraysWithAccess = [] as Set
		new File(analysis.database, "ArrayAccess.csv").eachLine {
			def (String array, String rawAp) = it.split("\t")
			if (!rawAp.contains("@?")) return
			def (String relName, name, String types, int dimensions) = arrayMeta[array]
			def parts = rawAp.split("@").drop(1)
			def last = fixVal(parts.last(), types)
			def indexes = (parts.dropRight(1) + [last]).toList().withIndex()
					.collect { t, int i -> t == "?" ? "i$i" : t}.join(", ")
			outFile << "$relName($indexes) :- ${relName}_Provided($indexes).\n"
			arraysWithAccess << array
		}

		outFile << "\n"
		arraysWithAccess.each { array ->
			def (String relName, name, String types, int dimensions) = arrayMeta[array]
			def dims = (0..dimensions).collect { "i$it" }.join(", ")
			outFile << "${relName}_Missing($dims) :-\n\t${relName}_Provided($dims),\n\t!$relName($dims).\n"
		}
	}

	static def conditions() {
		Map<String, Expr> ifReturnsExpr = [:]
		new File(analysis.database, "OUT_IfReturnsStr.csv").eachLine {
			def (String stmt, String rawAP) = it.split("\t")
			ifReturnsExpr[stmt] = ap(rawAP, "ret")
		}
		def methodsWithRules = []
		def ruleDecls = []
		new File(analysis.database, "OUT_IfGroupConditionStr.csv").eachLine {
			def (String stmt, String methodName, String complexCond) = it.split("\t")
			def conditions = complexCond.split(" AND ")
			def res = [new RelExpr("ret", methodName)] as List<Expr>
			conditions.eachWithIndex { cond, index ->
				def (String left, String op, String right) = cond.split("\\|")
				def l = ap(left, "tmp1$index")
				def r = ap(right, "tmp2$index")
				res += [l, r, new CompExpr(null, l.tempVar, op == "==" ? "=" : op, r.tempVar)]
			}
			res = Expr.opt(res + ifReturnsExpr[stmt])
			if (methodName !in methodsWithRules) ruleDecls << ".decl $methodName(value:symbol)"
			outFile << "\n${res[0].str()} :-\n\t"
			outFile << "${res.drop(1).collect { it.str() }.join(",\n\t")}.\n"
			methodsWithRules << methodName
		}
		new File(analysis.database, "OUT_NoIfReturnsStr.csv").eachLine {
			def (String methodName, String rawAP) = it.split("\t")
			if (methodName !in methodsWithRules) return
			def res = [new RelExpr("ret", methodName + "_Def"),
					new RelExpr("_", "!" + methodName), ap(rawAP, "ret")]
			res = Expr.opt(res)
			ruleDecls << ".decl ${methodName}_Def(value:symbol)"
			outFile << "\n${res[0].str()} :-\n\t"
			outFile << "${res.drop(1).collect { it.str() }.join(",\n\t")}.\n"
		}
		outFile << "\n"
		ruleDecls.each { outFile << "$it\n" }
	}

	static def schema() {
		Map<String, List<String[]>> classInfo = [:].withDefault { [] }
		new File(analysis.database, "OUT_ClassInfo.csv").eachLine { line ->
			def (klass, kind, field, fieldType) = line.split("\t")
			classInfo[klass] << [kind, field, fieldType]
		}

		def dlTypes = [] as Set
		def dlDecls = []
		def dlInputs = []
		def m = { def type ->
			switch (type) {
				case "int": return "number"
				case "java.lang.String": return "symbol"
				case "java.lang.String[]":
					dlTypes << ".type AR__symbol = [ head:symbol, rest:AR__symbol ]"
					return "AR__symbol"
				case ~/.*\[\]/:
					def t = type[0..-3]
					dlTypes << ".type AR_$t = [ head:$t, rest:AR_$t ]"
					return "AR_$t"
				default: return type
			}
		}

		classInfo.each { klass, relations ->
			dlTypes << ".type $klass"
			relations.each {
				def (kind, field, fieldType) = it
				String name
				switch (kind) {
					case "r":
						name = "${klass}_${field}"
						dlDecls << ".decl $name(this:$klass, $field:${m(fieldType)})"
						break
					case "R":
						name = "${klass}_CL_${field}"
						dlDecls << ".decl $name($field:${m(fieldType)})"
						break
					case "r[]":
						name = "${klass}_AR_${field}"
						dlDecls << ".decl $name(this:$klass, index:number, elem:${m(fieldType)})"
						break
					case "R[]":
						name = "${klass}_CL_AR_${field}"
						dlDecls << ".decl $name($field:${m(fieldType)})"
						break
					default:
						println "ERROR"
				}
				dlInputs << ".input $name"
			}
			def allFields = relations.findAll { it[0] == "r" }.collect { "${it[1]}:${m(it[2])}" }.join(", ")
			dlDecls << ".decl ${klass}_ALL(this:symbol, $allFields)"
			dlInputs << ".input ${klass}_ALL"
		}
		outFile << "\n"
		dlTypes.each { outFile << "$it\n" }
		dlDecls.each { outFile << "$it\n" }
		dlInputs.each { outFile << "$it\n" }
	}

	static def ap(String rawAP, String tempVar) {
		def clean = { String s -> s.isNumber() ? s : s.split("/").last().split('_\\$\\$A_').first() }
		def parts = rawAP.split("@")
		if (parts.length == 1) {
			return new CompExpr(tempVar, tempVar, "=", clean(parts[0]))
		} else {
			def array = arrayMeta[parts[0]].first() as String
			def indexes = parts.drop(1).collect { clean(it) }
			return new ArrayExpr(tempVar, array, indexes)
		}
	}
}

@Canonical
abstract class Expr {
	String tempVar

	abstract String str()

	boolean eq(Expr o) { false }

	void replace(String orig, String repl) { if (tempVar == orig) tempVar = repl }

	static List<Expr> opt(List<Expr> exprs) {
		def dupsMap = [:].withDefault { [] }
		exprs.each {expr ->
			if (expr !instanceof ArrayExpr || dupsMap.containsKey(expr)) return
			exprs.eachWithIndex{ Expr e, int i ->
				if (expr !== e && expr.eq(e)) dupsMap[expr] += i
			}
		}
		dupsMap.findAll{it.value.size() }.each { Expr e, List<Integer> dups ->
			dups.each { i ->
				def orig = exprs[i].tempVar
				exprs.each {it?.replace(orig, e.tempVar) }
				exprs[i] = null
			}
		}
		exprs.eachWithIndex { Expr e, int i ->
			if (e == null || e !instanceof CompExpr || e.op != "=") return
			def (orig, repl) = e.left.isNumber() ? [e.right, e.left] : [e.left, e.right]
			exprs.each {it?.replace(orig, repl) }
			exprs[i] = null
		}
		return exprs.grep()
	}
}

@Canonical(includeSuperProperties = true)
class RelExpr extends Expr {
	String relName

	String str() { "$relName($tempVar)" }
}

@Canonical(includeSuperProperties = true)
class ArrayExpr extends Expr {
	String array
	List<String> indexes

	String str() { "$array(${indexes.join(", ")}, $tempVar)" }

	boolean eq(Expr o) {
		if (o !instanceof ArrayExpr || array != o.array) return false
		indexes == o.indexes
	}
}

@Canonical(includeSuperProperties = true)
class CompExpr extends Expr {
	String left
	String op
	String right

	String str() { "$left $op $right" }

	void replace(String orig, String repl) {
		if (left == orig) left = repl
		if (right == orig) right = repl
	}
}