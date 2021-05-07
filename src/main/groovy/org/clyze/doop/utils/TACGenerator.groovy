package org.clyze.doop.utils

class TACGenerator {
	static void run(File runDir, File outFile) {
		def appClass = []
		new File("$runDir/ApplicationClass.facts").eachLine {appClass << it }

		def methods = []
		def method2Stmt = [:]

		def formals = [:].withDefault { [] }
		new File("$runDir/FormalParam.facts").eachLine {
			def (pos, m, param) = it.split("\t")
			formals[m][pos as int] = V(param)
		}
		def isNative = []
		new File("$runDir/NativeMethodId.facts").eachLine {
			def (m, name) = it.split("\t")
			isNative << m
		}
		new File("$runDir/Method.facts").eachLine {
			def (m, name, params, declT, retT, jvmDesc, arity) = it.split("\t")
			methods << m
			method2Stmt[m] = [:] as TreeMap
		}

		new File("$runDir/AssignNumConstant.facts").eachLine {
			def (stmt, index, value, to, inmethod) = it.split("\t")
			method2Stmt[inmethod][index as int] = INS(index, stmt, "${V(to)} = $value")
		}
		new File("$runDir/AssignLocal.facts").eachLine {
			def (stmt, index, from, to, inmethod) = it.split("\t")
			method2Stmt[inmethod][index as int] = INS(index, stmt, "${V(to)} = ${V(from)}")
		}
		new File("$runDir/AssignCast.facts").eachLine {
			def (stmt, index, from, to, type, inmethod) = it.split("\t")
			method2Stmt[inmethod][index as int] = INS(index, stmt, "${V(to)} = ($type) ${V(from)}")
		}
		new File("$runDir/LoadInstanceField.facts").eachLine {
			def (stmt, index, to, base, fld, inmethod) = it.split("\t")
			method2Stmt[inmethod][index as int] = INS(index, stmt, "${V(to)} = ${V(base)}.$fld")
		}
		new File("$runDir/StoreInstanceField.facts").eachLine {
			def (stmt, index, from, base, fld, inmethod) = it.split("\t")
			method2Stmt[inmethod][index as int] = INS(index, stmt, "${V(base)}.$fld = ${V(from)}")
		}
		new File("$runDir/LoadStaticField.facts").eachLine {
			def (stmt, index, to, fld, inmethod) = it.split("\t")
			method2Stmt[inmethod][index as int] = INS(index, stmt, "${V(to)} = $fld")
		}
		new File("$runDir/StoreStaticField.facts").eachLine {
			def (stmt, index, from, fld, inmethod) = it.split("\t")
			method2Stmt[inmethod][index as int] = INS(index, stmt, "$fld = ${V(from)}")
		}
		new File("$runDir/Return.facts").eachLine {
			def (stmt, index, var, inmethod) = it.split("\t")
			method2Stmt[inmethod][index as int] = INS(index, stmt, "return ${V(var)}")
		}
		new File("$runDir/ReturnVoid.facts").eachLine {
			def (stmt, index, inmethod) = it.split("\t")
			method2Stmt[inmethod][index as int] = INS(index, stmt, "return")
		}
		new File("$runDir/Goto.facts").eachLine {
			def (stmt, index, label, inmethod) = it.split("\t")
			method2Stmt[inmethod][index as int] = INS(index, stmt, "goto $label")
		}

		def switchDef = [:], switchCases = [:].withDefault { [:] }
		new File("$runDir/TableSwitch-Default.facts").eachLine {
			def (stmt, target) = it.split("\t")
			switchDef[stmt] = target
		}
		new File("$runDir/LookupSwitch-Default.facts").eachLine {
			def (stmt, target) = it.split("\t")
			switchDef[stmt] = target
		}
		new File("$runDir/TableSwitch-Target.facts").eachLine {
			def (stmt, value, index) = it.split("\t")
			switchCases[stmt][value] = index
		}
		new File("$runDir/LookupSwitch-Target.facts").eachLine {
			def (stmt, value, index) = it.split("\t")
			switchCases[stmt][value] = index
		}
		new File("$runDir/TableSwitch.facts").eachLine {
			def (stmt, index, key, inmethod) = it.split("\t")
			def line1 = INS(index, stmt, "tableSwitch (${V(key)})")
			def line2 = "\tdefault: goto ${switchDef[stmt]}"
			def lines = switchCases[stmt].collect { value, i -> "\tcase $value: goto $i" }.join("\n")
			method2Stmt[inmethod][index as int] = "$line1\n$line2\n$lines"
		}
		new File("$runDir/LookupSwitch.facts").eachLine {
			def (stmt, index, key, inmethod) = it.split("\t")
			def line1 = INS(index, stmt, "lookupSwitch (${V(key)})")
			def line2 = "\tdefault: goto ${switchDef[stmt]}"
			def lines = switchCases[stmt].collect { value, i -> "\tcase $value: goto $i" }.join("\n")
			method2Stmt[inmethod][index as int] = "$line1\n$line2\n$lines"
		}

		new File("$runDir/EnterMonitor.facts").eachLine {
			def (stmt, index, var, inmethod) = it.split("\t")
			method2Stmt[inmethod][index as int] = INS(index, stmt, "enterMonitor ${V(var)}")
		}
		new File("$runDir/ExitMonitor.facts").eachLine {
			def (stmt, index, var, inmethod) = it.split("\t")
			method2Stmt[inmethod][index as int] = INS(index, stmt, "exitMonitor ${V(var)}")
		}

		def ret = [:], params = [:].withDefault { [] }
		new File("$runDir/AssignReturnValue.facts").eachLine {
			def (stmt, var) = it.split("\t")
			ret[stmt] = V(var)
		}
		new File("$runDir/ActualParam.facts").eachLine {
			def (pos, stmt, param) = it.split("\t")
			params[stmt][pos as int] = V(param)
		}
		new File("$runDir/VirtualMethodInvocation.facts").eachLine {
			def (stmt, index, method, base, inmethod) = it.split("\t")
			def p = params[stmt].join(", ")
			method2Stmt[inmethod][index as int] = INS(index, stmt, "${ret[stmt] ? "${ret[stmt]} = " : ""}${V(base)}.$method($p)")
		}
		new File("$runDir/SpecialMethodInvocation.facts").eachLine {
			def (stmt, index, method, base, inmethod) = it.split("\t")
			def p = params[stmt].join(", ")
			method2Stmt[inmethod][index as int] = INS(index, stmt, "${ret[stmt] ? "${ret[stmt]} = " : ""}${V(base)}.$method($p)")
		}
		new File("$runDir/StaticMethodInvocation.facts").eachLine {
			def (stmt, index, method, inmethod) = it.split("\t")
			def p = params[stmt].join(", ")
			method2Stmt[inmethod][index as int] = INS(index, stmt, "${ret[stmt] ? "${ret[stmt]} = " : ""}$method($p)")
		}
		ret = null
		params = null

		def oper = [:], operand = [:].withDefault { [] }
		new File("$runDir/OperatorAt.facts").eachLine {
			def (stmt, op) = it.split("\t")
			oper[stmt] = op
		}
		new File("$runDir/AssignOperFrom.facts").eachLine {
			def (stmt, pos, from) = it.split("\t")
			operand[stmt][pos as int] = V(from)
		}
		new File("$runDir/AssignOperFromConstant.facts").eachLine {
			def (stmt, pos, from) = it.split("\t")
			operand[stmt][pos as int] = from
		}
		new File("$runDir/IfVar.facts").eachLine {
			def (stmt, pos, from) = it.split("\t")
			operand[stmt][pos as int] = V(from)
		}
		new File("$runDir/IfConstant.facts").eachLine {
			def (stmt, pos, from) = it.split("\t")
			operand[stmt][pos as int] = V(from)
		}
		new File("$runDir/AssignUnop.facts").eachLine {
			def (stmt, index, to, inmethod) = it.split("\t")
			method2Stmt[inmethod][index as int] = INS(index, stmt, "${V(to)} = ${oper[stmt]} ${operand[stmt][1]}")
		}
		new File("$runDir/AssignBinop.facts").eachLine {
			def (stmt, index, to, inmethod) = it.split("\t")
			method2Stmt[inmethod][index as int] = INS(index, stmt, "${V(to)} = ${operand[stmt][1]} ${oper[stmt]} ${operand[stmt][2]}")
		}
		new File("$runDir/If.facts").eachLine {
			def (stmt, index, label, inmethod) = it.split("\t")
			method2Stmt[inmethod][index as int] = INS(index, stmt, "if (${operand[stmt][1]} ${oper[stmt]} ${operand[stmt][2]}) goto $label")
		}
		oper = null
		operand = null

		def idx = [:]
		new File("$runDir/ArrayInsnIndex.facts").eachLine {
			def (stmt, indexVar) = it.split("\t")
			idx[stmt] = V(indexVar)
		}
		new File("$runDir/ArrayNumIndex.facts").eachLine {
			def (stmt, index) = it.split("\t")
			idx[stmt] = index
		}
		new File("$runDir/LoadArrayIndex.facts").eachLine {
			def (stmt, index, to, arr, inmethod) = it.split("\t")
			method2Stmt[inmethod][index as int] = INS(index, stmt, "${V(to)} = ${V(arr)}[${idx[stmt]}]")
		}
		new File("$runDir/StoreArrayIndex.facts").eachLine {
			def (stmt, index, from, arr, inmethod) = it.split("\t")
			method2Stmt[inmethod][index as int] = INS(index, stmt, "${V(arr)}[${idx[stmt]}] = ${V(from)}")
		}
		idx = null

		def arraySize = [:], isStr = [], isEmpty = []
		new File("$runDir/ArrayAllocation.facts").eachLine {
			def (stmt, sizeVar) = it.split("\t")
			arraySize[stmt] = V(sizeVar)
		}
		new File("$runDir/StringConstant.facts").eachLine {isStr << it }
		new File("$runDir/EmptyArray.facts").eachLine {isEmpty << it }
		new File("$runDir/AssignHeapAllocation.facts").eachLine {
			def (stmt, index, heap, to, inmethod, line) = it.split("\t")
			def alloc = "new $heap"
			if (heap in isStr) alloc = "\"$heap\""
			else if (arraySize[stmt]) alloc = "new $heap[${arraySize[stmt]}]"
			else if (heap in isEmpty) alloc = "new $heap[]"
			method2Stmt[inmethod][index as int] = INS(index, stmt, "${V(to)} = $alloc")
		}


		new File("$runDir/AssignCastNumConstant.facts").eachLine {
			def (stmt, index, val, to, type, inmethod) = it.split("\t")
			method2Stmt[inmethod][index as int] = INS(index, stmt, "${V(to)} = ($type) $val")
		}
		new File("$runDir/AssignCastNull.facts").eachLine {
			def (stmt, index, to, type, inmethod) = it.split("\t")
			method2Stmt[inmethod][index as int] = INS(index, stmt, "${V(to)} = ($type) NULL")
		}
		new File("$runDir/AssignNull.facts").eachLine {
			def (stmt, index, to, inmethod) = it.split("\t")
			method2Stmt[inmethod][index as int] = INS(index, stmt, "${V(to)} = NULL")
		}
		new File("$runDir/Throw.facts").eachLine {
			def (stmt, index, var, inmethod) = it.split("\t")
			method2Stmt[inmethod][index as int] = INS(index, stmt, "throw $var")
		}
		new File("$runDir/ThrowNull.facts").eachLine {
			def (stmt, index, inmethod) = it.split("\t")
			method2Stmt[inmethod][index as int] = INS(index, stmt, "throw NULL")
		}
		new File("$runDir/AssignInstanceOf.facts").eachLine {
			def (stmt, index, from, to, type, inmethod) = it.split("\t")
			method2Stmt[inmethod][index as int] = INS(index, stmt, "${V(to)} = ${V(from)} instanceof $type")
		}
		new File("$runDir/AssignPhantomInvoke.facts").eachLine {
			def (stmt, index, inmethod) = it.split("\t")
			method2Stmt[inmethod][index as int] = INS(index, stmt, "UNSUPPORTED <<phantom invoke>>")
		}
		new File("$runDir/UnsupportedInstruction.facts").eachLine {
			def (stmt, index, inmethod) = it.split("\t")
			method2Stmt[inmethod][index as int] = INS(index, stmt, "UNSUPPORTED <<??>>")
		}


		method2Stmt.each { methodName, methodInfo ->
			outFile << "$methodName (${formals[methodName].join(", ")}) {\n"
			if (methodName in isNative) outFile << "\t<<native code>>\n"
			else methodInfo.each { index, line -> outFile << "\t$line\n" }
			outFile << "}\n"
		}
	}

	static def V(String v) {
		def parts = v.split('/')
		v.contains("/intermediate/") ? parts.takeRight(3).join("/") : parts.last()
	}

	static def INS(def index, String id, def body) { sprintf("%2s: %-60s   // ${id.split('\\)>/').last()}", index, body) }
}
