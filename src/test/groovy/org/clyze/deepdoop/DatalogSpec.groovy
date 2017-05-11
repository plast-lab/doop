package org.clyze.deepdoop

import org.antlr.v4.runtime.ANTLRInputStream
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.tree.ParseTreeWalker
import org.clyze.deepdoop.actions.FlatteningActor
import org.clyze.deepdoop.actions.IVisitable
import org.clyze.deepdoop.actions.LBCodeGenVisitingActor
import org.clyze.deepdoop.actions.PostOrderVisitor
import org.clyze.deepdoop.datalog.DatalogLexer
import org.clyze.deepdoop.datalog.DatalogListenerImpl
import org.clyze.deepdoop.datalog.DatalogParser
import org.clyze.deepdoop.datalog.Program
import org.clyze.deepdoop.system.*
import spock.lang.Specification
import spock.lang.Unroll

class DatalogSpec extends Specification  {

	@Unroll
	def "DeepDoop passing tests"() {
		when:
		test(file)

		then:
		notThrown(DeepDoopException)

		where:
		file        | _
		"t1.logic"  | _
		"t2.logic"  | _
		"t3.logic"  | _
		"t4.logic"  | _
		"t5.logic"  | _
		"t6.logic"  | _
		"t7.logic"  | _
		"t8.logic"  | _
		"t9.logic"  | _
		"t10.logic" | _
		"t11.logic" | _
		"t12.logic" | _
		"t13.logic" | _
		"t14.logic" | _
		//"sample.logic" | _
		//"analysis/cfg-tests.logic" | _
		//"analysis/context-insensitive-declarations.logic" | _
		//"analysis/context-insensitive.logic" | _
		//"analysis/facts-declarations.logic" | _
		//"analysis/flow-insensitivity-declarations.logic" | _
		//"analysis/statistics-simple.logic" | _
		//"analysis/tamiflex-declarations.logic" | _
		//"analysis/tamiflex-fact-declarations.logic" | _
		//"analysis/tamiflex.logic" | _
	}

	@Unroll
	def "DeepDoop failing tests"() {
		when:
		test(file)

		then:
		def e = thrown(DeepDoopException)
		e.errorId == expectedErrorId

		where:
		file           | expectedErrorId
		"fail1.logic"  | ErrorId.DEP_CYCLE
		"fail2.logic"  | ErrorId.DEP_GLOBAL
		"fail3.logic"  | ErrorId.CMD_RULE
		"fail4.logic"  | ErrorId.CMD_CONSTRAINT
		"fail5.logic"  | ErrorId.CMD_DIRECTIVE
		"fail6.logic"  | ErrorId.CMD_NO_DECL
		"fail7.logic"  | ErrorId.CMD_NO_IMPORT
		"fail8.logic"  | ErrorId.CMD_EVAL
		"fail9.logic"  | ErrorId.ID_IN_USE
		"fail10.logic" | ErrorId.UNKNOWN_VAR
		"fail11.logic" | ErrorId.UNKNOWN_COMP
		"fail12.logic" | ErrorId.UNKNOWN_COMP
		"fail13.logic" | ErrorId.MULTIPLE_ENT_DECLS
		"fail14.logic" | ErrorId.UNKNOWN_PRED
		"fail15.logic" | ErrorId.NO_DECL_REC
	}


	def test(String file) {
		def resourcePath = "/deepdoop/$file"
		def resource = this.class.getResource(resourcePath).file
		def walker = new ParseTreeWalker()
		def listener = new DatalogListenerImpl(resource)
		def tree = new DatalogParser(
			new CommonTokenStream(
				new DatalogLexer(
					new ANTLRInputStream(this.class.getResourceAsStream(resourcePath))))).program()

		walker.walk(listener, tree)

		def p = listener.program

		def v = new PostOrderVisitor<IVisitable>(new FlatteningActor(p.comps))
		def flatP = p.accept(v) as Program

		flatP.accept(new LBCodeGenVisitingActor("build/"))
	}
}
