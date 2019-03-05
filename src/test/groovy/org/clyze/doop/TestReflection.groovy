package org.clyze.doop

import org.clyze.analysis.Analysis
import org.clyze.doop.core.Doop
import spock.lang.Specification
import spock.lang.Unroll
import static org.clyze.doop.TestUtils.*

class TestReflection extends ServerAnalysisTests {
	// @spock.lang.Ignore
	@Unroll
	def "Server analysis test 016 (reflection)"() {
		when:
		Analysis analysis = analyzeTest("016-reflection",
										["--reflection-classic", "--reflection-dynamic-proxies",
										 "--gen-opt-directives", "--Xserver-logic", "--generate-jimple",
										 "--Xextra-logic", "${Doop.souffleAddonsPath}/testing/TestReflection.dl",
										 "--thorough-fact-gen", "--sanity",
										 "--platform", "java_8"])

		then:
		testSucceeds(analysis, "test1.1")
		testSucceeds(analysis, "test1.2")
		testSucceeds(analysis, "test1.3")
		testSucceeds(analysis, "test1.4")
		testSucceeds(analysis, "test1.5")
		testSucceeds(analysis, "test1.6")
		testSucceeds(analysis, "test1.7")
		testSucceeds(analysis, "test1.8")
		testSucceeds(analysis, "test1.9")
		testSucceeds(analysis, "test1.10")
		testSucceeds(analysis, "test1.11")
		noSanityErrors(analysis)
	}

	// @spock.lang.Ignore
	@Unroll
	def "Server analysis test 016 (light reflection glue)"() {
		when:
		Analysis analysis = analyzeTest("016-reflection",
										["--light-reflection-glue",
										 "--Xextra-logic", "${Doop.souffleAddonsPath}/testing/test-exports.dl",
										 "--thorough-fact-gen", "--sanity",
										 "--platform", "java_8"],
										'context-insensitive',
										'test-016-reflection-glue')

		then:
		varPointsToQ(analysis, '<Main: void main(java.lang.String[])>/cA#_31', '<class A>')
		noSanityErrors(analysis)
	}
}
