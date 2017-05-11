package org.clyze.deepdoop.system

import org.antlr.v4.runtime.ANTLRFileStream
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.tree.ParseTreeWalker
import org.apache.commons.logging.LogFactory
import org.clyze.deepdoop.actions.*
import org.clyze.deepdoop.datalog.*

class Compiler {

	static List<Result> compileToLB(String filename, File outDir) {
		compile(filename, new LBCodeGenVisitingActor(outDir))
	}

	static List<Result> compileToSouffle(String filename, File outDir) {
		compile(filename, new SouffleCodeGenVisitingActor(outDir))
	}

	private static List<Result> compile(String filename, DefaultCodeGenVisitingActor codeGenActor) {
		LogFactory.getLog(Compiler.class).info("[DD] COMPILE: $filename with ${codeGenActor.class.name}")

		def parser = new DatalogParser(
				new CommonTokenStream(
					new DatalogLexer(
						new ANTLRFileStream(filename))))
		def listener = new DatalogListenerImpl(filename)
		ParseTreeWalker.DEFAULT.walk(listener, parser.program())

		codeGenActor.visit(listener.program)
		return codeGenActor.results
	}
}
