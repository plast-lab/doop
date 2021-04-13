package org.clyze.doop

import org.clyze.analysis.Analysis
import spock.lang.Unroll
import static org.clyze.doop.TestUtils.*

/**
 * Test Differential Datalog (ddlog) mode.
 */
class TestDifferentialDatalog extends DoopSpec {
	// @spock.lang.Ignore
	@Unroll
	def "Differential Datalog test"() {
		when:
		Analysis analysis = analyzeTest("006-hello-world--via-ddlog", Artifacts.HELLO_JAR,
										["--Xvia-ddlog", "--stats", "none", "--souffle-force-recompile"],
                                        "micro", "hello-world--via-ddlog")

		then:
        fileExists(analysis, 'database/dump')
	}
}
