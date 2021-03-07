package org.clyze.doop

import org.clyze.analysis.Analysis
import org.clyze.doop.core.Doop
import spock.lang.Specification
import spock.lang.Unroll
import static org.clyze.doop.TestUtils.*

class TestClassicReflection extends DoopSpec {
	// @spock.lang.Ignore
	@Unroll
	def "Server analysis test 016 (reflection)"() {
		when:
		List options = ["--reflection-classic", "--reflection-dynamic-proxies",
						"--gen-opt-directives", "--server-logic", "--generate-jimple",
						"--extra-logic", "${Doop.souffleLogicPath}/addons/testing/TestReflection.dl",
						"--thorough-fact-gen", "--no-standard-exports",
						"--platform", "java_8"] + sanityOpts
		Analysis analysis = analyzeBuiltinTest("016-reflection", options)

		then:
		// Reflection tests.
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
		// Annotation tests.
		testSucceeds(analysis, "test2.1")
		testSucceeds(analysis, "test2.2")
		testSucceeds(analysis, "test2.3")
		testSucceeds(analysis, "test2.4")
		testSucceeds(analysis, "test2.5")
		testSucceeds(analysis, "test2.6")
		testSucceeds(analysis, "test2.7")
		testSucceeds(analysis, "test2.8")
		testSucceeds(analysis, "test2.9")
		noSanityErrors(analysis)
	}
}
