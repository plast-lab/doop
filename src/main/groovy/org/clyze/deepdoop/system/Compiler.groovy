package org.clyze.deepdoop.system

import org.antlr.v4.runtime.ANTLRFileStream
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.tree.ParseTreeWalker
import org.apache.commons.logging.LogFactory
import org.clyze.deepdoop.actions.*
import org.clyze.deepdoop.datalog.*

class Compiler {

	static List<Result> compile(String outDir, String filename) {
		LogFactory.getLog(Compiler.class).info("[DD] COMPILE: $filename")

		def parser = new DatalogParser(
				new CommonTokenStream(
					new DatalogLexer(
						new ANTLRFileStream(filename))))
		def listener = new DatalogListenerImpl(filename)
		ParseTreeWalker.DEFAULT.walk(listener, parser.program())

		def p = listener.program
		def flatP = p.accept(new PostOrderVisitor<IVisitable>(new FlatteningActor(p.comps))) as Program

		def codeGenActor = new LBCodeGenVisitingActor(outDir)
		codeGenActor.visit(flatP)
		return codeGenActor.getResults()
	}
}
