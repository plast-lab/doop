package org.clyze.deepdoop

import org.clyze.analysis.Helper
import org.clyze.deepdoop.system.Compiler

Helper.initLogging("INFO", System.getenv("DOOP_HOME") + "/build/logs", true)

try {
	//println Compiler.compileToLB(args[0], new File("build"))
	println "----"
	println Compiler.compileToSouffle(args[0], new File("build"))
} catch (Exception e) {
	e.printStackTrace()
}
