package org.clyze.doop.utils

import org.clyze.doop.core.DoopAnalysis

class XTractor {
	static void run(DoopAnalysis analysis) {
		def outFile = new File(analysis.database, "xtractor-out.dl")
		outFile.text = ""

		def arrayMeta = [:].withDefault { [] }
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
		def arraySizes = [:].withDefault { [:] }
		new File(analysis.database, "META_ArraySizes.csv").eachLine {
			def (String array, pos, size) = it.split("\t")
			arraySizes[array][pos as int] = size
		}
		arrayMeta.each { array, meta ->
			def (String relName, metaRule, name, types, int dimensions) = meta
			def sizes = arraySizes[array]
			def allSizes = (0..(dimensions-1)).collect {sizes[it] ?: -1 }
			outFile << "$metaRule\n"
			outFile << "${relName}_DimSizes(${allSizes.join(", ")}).\n"
		}
		arraySizes = null

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
		arrayMeta = null
		arrayFrom2Index2Var = null
		arrayToIndexHasValue = null



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
