package org.clyze.doop

import org.clyze.analysis.Analysis
import org.clyze.doop.core.Doop
import spock.lang.Specification
import spock.lang.Unroll
import static org.clyze.doop.TestUtils.*

/**
 * Test small programs from the server-analysis-tests repo.
 */
class SimpleAnalysisTests extends ServerAnalysisTests {

	// @spock.lang.Ignore
	@Unroll
	def "Server analysis test 006 (hello world) / test additional command line options"() {
		when:
		Analysis analysis = analyzeTest("006-hello-world",
										["--coarse-grained-allocation-sites",
										 "--cs-library", "--Xextra-metrics",
										 "--dont-report-phantoms", "--cfg",
										 "--thorough-fact-gen", "--sanity",
										 "--platform", "java_8"])

		then:
		noSanityErrors(analysis)
	}

	// @spock.lang.Ignore
	@Unroll
	def "Server analysis test 011 (variance)"() {
		when:
		Analysis analysis = analyzeTest("011-variance",
										["--Xextra-logic", "${Doop.souffleAddonsPath}/testing/test-exports.dl",
										 "--generate-jimple", "--Xserver-logic",
										 "--thorough-fact-gen", "--sanity"])

		then:
		invoValue(analysis, '<Main: void main(java.lang.String[])>/I.meth1/0', '<Test: java.lang.Object meth1(Clazz)>')
		invoValue(analysis, '<Main: void main(java.lang.String[])>/I.meth1/1', '<Test: java.lang.Object meth1(Clazz)>')
		invoValue(analysis, '<Main: void main(java.lang.String[])>/I.meth2/0', '<Test: Clazz meth2(java.lang.Object)>')
		invoValue(analysis, '<Main: void main(java.lang.String[])>/I.meth2/0', '<Test: SubClazz meth2(java.lang.Object)>')
		methodIsReachable(analysis, '<Test: SubClazz meth2(java.lang.Object)>')
		methodSub(analysis, '<I: Clazz meth2(java.lang.Object)>', '<Test: SubClazz meth2(java.lang.Object)>')
		noSanityErrors(analysis)
	}

	// @spock.lang.Ignore
	@Unroll
	def "Server analysis test 012 (interface fields)"() {
		when:
		Analysis analysis = analyzeTest("012-interface-fields", ["--Xextra-logic", "${Doop.souffleAddonsPath}/testing/test-exports.dl"])

		then:
		staticFieldPointsTo(analysis, '<Y: Y fooooooooo>', '<Y: void <clinit>()>/new Y$1/0')
	}

	// @spock.lang.Ignore
	@Unroll
	def "Server analysis test 013 (enums)"() {
		when:
		Analysis analysis = analyzeTest("013-enums", ["--reflection-classic", "--generate-jimple", "--Xextra-logic", "${Doop.souffleAddonsPath}/testing/test-exports.dl"])

		then:
		varPointsTo(analysis, '<Main: void main(java.lang.String[])>/enumConsts#_93', '<Enums array for Main$UndeletablePrefKey>')
		arrayIndexPointsTo(analysis, '<Enums array for Main$UndeletablePrefKey>', '<Main$UndeletablePrefKey: void <clinit>()>/new Main$UndeletablePrefKey/0', true)
	}
}
