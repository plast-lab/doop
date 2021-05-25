package org.clyze.doop.utils

import groovy.transform.Canonical
import org.clyze.doop.core.DoopAnalysis

import java.lang.reflect.Array

class XTractor {
	static DoopAnalysis analysis
	static File outFile

	static def arrayMeta = [:].withDefault { [] }

	static void run(DoopAnalysis analysis) {
		this.analysis = analysis
		outFile = new File(analysis.database, "xtractor-out.dl")
		outFile.text = ""

		arrays()
		conditions()
		schema()

		println "Results in... $outFile"
	}

	private static def arrays() {
		Map<String, Integer> relNameToVariant = [:]
		outFile << ".decl arr_META(relation:symbol, name:symbol, types:symbol, dimensions:number)\n"
		new File(analysis.database, "OUT_ArrayInfo.csv").eachLine {
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
			def dims = (1..dimensions).collect { "i$it:symbol" }.join(", ")
			outFile << ".decl $relName($dims, value:symbol)\n"
			def dimSizes = (1..dimensions).collect { "dim$it:number" }.join(", ")
			outFile << ".decl ${relName}_DimSizes($dimSizes)\n"
			def metaRule = "arr_META(\"$relName\", \"$array\", \"$types\", $dimensions)."
			arrayMeta[array] = [relName, metaRule, name, types, dimensions]
		}
		def arrayDims = [:].withDefault { [:] }
		new File(analysis.database, "OUT_ArrayDims.csv").eachLine {
			def (String array, pos, size) = it.split("\t")
			arrayDims[array][pos as int] = size
		}
		arrayMeta.each { array, meta ->
			def (String relName, metaRule, name, types, int dimensions) = meta
			def sizes = arrayDims[array]
			def allSizes = (0..(dimensions - 1)).collect { sizes[it] ?: -1 }
			outFile << "$metaRule\n"
			outFile << "${relName}_DimSizes(${allSizes.join(", ")}).\n"
		}

		def load_from2index2to = [:].withDefault { [:].withDefault { [] } }
		new File(analysis.database, "OUT_ArrayLoad.csv").eachLine {
			def (String to, String from, index) = it.split("\t")
			load_from2index2to[from][index as int] << to
		}
		def store_to_index_value = [:].withDefault { [:] }
		new File(analysis.database, "OUT_ArrayStore.csv").eachLine {
			def (String to, index, value) = it.split("\t")
			store_to_index_value[to][index as int] = value
		}

		def appendToIndices
		appendToIndices = { String array, List indices, String currVar ->
			def nextIndicesAndVars = load_from2index2to[currVar]
			if (nextIndicesAndVars.isEmpty()) {
				def relName = arrayMeta[array].first()
				store_to_index_value[currVar].each { lastIndex, value ->
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
	}

	private static def conditions() {
		def clean = { String s ->
			s.isNumber() ? s : s.split("/").last().split('_\\$\\$A_').first()
		}

		def ap = { String s, String tempVar ->
			def parts = s.split("@")
			if (parts.length == 1) {
				return new CompExpr(tempVar, tempVar, "=", clean(parts[0]))
			} else {
				def array = arrayMeta[parts[0]].first() as String
				def indexes = parts.drop(1).collect { clean(it) }
				return new ArrayExpr(tempVar, array, indexes)
			}
		}

		Map<String, Expr> ifReturnsExpr = [:]
		new File(analysis.database, "OUT_IfReturnsStr.csv").eachLine {
			def (String stmt, String rawAP) = it.split("\t")
			ifReturnsExpr[stmt] = ap(rawAP, "ret")
		}
		def methodsWithRules = []
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
			outFile << "\n${res[0].str()} :-\n\t"
			outFile << "${res.drop(1).collect { it.str() }.join(",\n\t")}.\n"
			methodsWithRules << methodName
		}
		new File(analysis.database, "OUT_NoIfReturnsStr.csv").eachLine {
			def (String methodName, String rawAP) = it.split("\t")
			if (methodName !in methodsWithRules) return
			def res = [new RelExpr("ret", methodName + "_default"),
					new RelExpr("_", "!" + methodName), ap(rawAP, "ret")]
			res = Expr.opt(res)
			outFile << "\n${res[0].str()} :-\n\t"
			outFile << "${res.drop(1).collect { it.str() }.join(",\n\t")}.\n"
		}
	}

	private static def schema() {
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