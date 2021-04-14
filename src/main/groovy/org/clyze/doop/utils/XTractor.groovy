package org.clyze.doop.utils

import org.clyze.doop.core.DoopAnalysis

class XTractor {
	static void run(DoopAnalysis analysis) {
		def outFile = new File(analysis.database, "xtractor-out.dl")
		outFile.text = ""

		Map<String, List<String[]>> classInfo = [:].withDefault { [] }
		new File(analysis.database, "Schema_ClassInfo.csv").eachLine { line ->
			def (klass, kind, field, fieldType) = line.split("\t")
			classInfo[klass] << [kind, field, fieldType]
		}

		outFile << ".decl ArrayRelation_Meta(relName:symbol, var:symbol, dimensions:number)\n"
		outFile << ".decl ArrayRelation_Dimension(relName:symbol, pos:number, size:number)\n"
		new File(analysis.database, "Schema_ArrayRelation.csv").eachLine {
			def (String relName, array, String types) = it.split("\t")
			def dimensions = types.count("[]")
			outFile << ".decl $relName(${(1..dimensions).collect { "i$it:symbol" }.join(", ")}, value:symbol)\n"
			outFile << "ArrayRelation_Meta(\"$relName\", \"$array\", $dimensions).\n"
		}
		new File(analysis.database, "Schema_ArraySizes.csv").eachLine {
			def (String relName, array, pos, size) = it.split("\t")
			outFile << "ArrayRelation_Dimension(\"$relName\", $pos, $size).\n"
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
