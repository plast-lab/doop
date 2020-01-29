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
	def "Server analysis test 006 (hello world) / test additional command line options"() {
		when:
		Analysis analysis = analyzeTest("006-hello-world", Artifacts.HELLO_JAR,
										["--via-ddlog", "--Xstats-none"],
                                        "micro", "hello-world--via-ddlog")

		then:
        fileExists(analysis, 'database/dump')
	}
}
