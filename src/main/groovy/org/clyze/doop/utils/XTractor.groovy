package org.clyze.doop.utils

import org.clyze.doop.core.DoopAnalysis

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
	}

	private static def arrays() {
		arrayMeta = [:].withDefault { [] }
		outFile << ".decl arr_META(name:symbol, types:symbol, dimensions:number)\n"
		new File(analysis.database, "META_ArrayInfo.csv").eachLine {
			def (String array, String name, String types) = it.split("\t")
			def dimensions = types.count("[]")
			def relName = "${name.split("#").first()}"
			def dims = (1..dimensions).collect { "i$it:symbol" }.join(", ")
			outFile << ".decl $relName($dims, value:symbol)\n"
			outFile << ".decl ${relName}_DimSizes(dim:number, size:number)\n"
			def metaRule = "arr_META(\"$array\", \"$types\", $dimensions)."
			arrayMeta[array] = [relName, metaRule, name, types, dimensions]
		}
		def arrayDims = [:].withDefault { [:] }
		new File(analysis.database, "META_ArrayDims.csv").eachLine {
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

		def arrayFrom2Index2Var = [:].withDefault { [:].withDefault { [] } }
		new File(analysis.database, "META_ArrayLoad.csv").eachLine {
			def (String to, String from, index) = it.split("\t")
			arrayFrom2Index2Var[from][index as int] << to
		}
		def arrayToIndexHasValue = [:].withDefault { [:] }
		new File(analysis.database, "META_ArrayStore.csv").eachLine {
			def (String to, index, value) = it.split("\t")
			arrayToIndexHasValue[to][index as int] = value
		}

		def appendToIndices
		appendToIndices = { String array, List indices, String currVar ->
			def nextIndicesAndVars = arrayFrom2Index2Var[currVar]
			if (nextIndicesAndVars.isEmpty()) {
				def relName = arrayMeta[array].first()
				arrayToIndexHasValue[currVar].each { lastIndex, value ->
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
		new File(analysis.database, "IF_ConditionSymbol.csv").eachLine {
			def (String stmt, String complexCond) = it.split("\t")
			def conditions = complexCond.split(" AND ")
			outFile << "$stmt\n"
			conditions.eachWithIndex { cond, index ->
				def (String left, op, String right) = cond.split("\\|")
				parseAP(left.split("@"), index, "l")
				parseAP(right.split("@"), index, "r")
				outFile << "lVal$index $op rVal$index,\n"
			}
		}
	}

	static def parseAP(def parts, int index, def side) {
		if (parts.size() == 1) {
			outFile << "${side}Val$index = ${parts.first()},\n"
		} else {
			def rel = arrayMeta[parts.first()].first()
			def indexes = parts.drop(1).collect{ it.split("/").last().split('_\\$\\$A_').first() }
			outFile << "$rel(${indexes.join(", ")}, ${side}Val$index),\n"
		}
	}

	private static def schema() {
		Map<String, List<String[]>> classInfo = [:].withDefault { [] }
		new File(analysis.database, "Schema_ClassInfo.csv").eachLine { line ->
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
		dlTypes.each { outFile << "$it\n" }
		dlDecls.each { outFile << "$it\n" }
		dlInputs.each { outFile << "$it\n" }

		println "Static Schema... Result in $outFile"
	}
}
