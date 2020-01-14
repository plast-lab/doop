package org.clyze.doop

import org.clyze.analysis.Analysis
import spock.lang.Unroll
import static org.clyze.doop.TestUtils.*

/**
 * Test WALA mode (fact generation and analysis).
 */
class CrudeWalaTest extends DoopSpec {

	// @spock.lang.Ignore
	@Unroll
	def "Crude testing WALA fact generation and analysis"(def mode) {
		when:
		Main.main((String[])(["-i", Artifacts.ANTLR_JAR, "-a", "context-insensitive", "--id", "antlr-wala", "--dacapo", "--wala-fact-gen", "--platform", "java_8", "-Ldebug"] + testExports + mode))
		Analysis analysis = Main.analysis

		then:
		relationHasApproxSize(analysis, "CallGraphEdge", 73000)
		relationHasApproxSize(analysis, "mainAnalysis.VarPointsTo", 3200000)
		relationHasApproxSize(analysis, "ReachableContext", 11000)

        where:
        mode << [[], ["--Xisolate-fact-generation"]]
	}
}
