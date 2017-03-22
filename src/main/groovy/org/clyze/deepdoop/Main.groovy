package org.clyze.deepdoop

import org.clyze.analysis.Helper
import org.clyze.deepdoop.system.Compiler

Helper.initLogging("INFO", System.getenv("DOOP_HOME") + "/build/logs", true)

try {
	println Compiler.compile("build", args[0])
} catch (Exception e) {
	e.printStackTrace()
}