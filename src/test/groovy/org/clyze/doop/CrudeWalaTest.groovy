package org.clyze.doop

import org.clyze.analysis.Analysis
import spock.lang.Specification
import spock.lang.Unroll
import static org.clyze.doop.TestUtils.*

/**
 * Test WALA mode (fact generation and analysis).
 */
class CrudeWalaTest extends Specification {

	Analysis analysis

	@Unroll
	def "Crude testing WALA fact generation and analysis"() {
		when:
		Main.main((String[])["-i", "http://centauri.di.uoa.gr:8081/artifactory/DaCapo-benchmarks/2006/antlr.jar", "-a", "context-insensitive", "--id", "antlr-wala", "--dacapo", "--wala-fact-gen", "--platform", "java_8", "--sanity"])
		analysis = Main.analysis

		then:
		relationHasApproxSize(analysis, "CallGraphEdge", 63974)
		relationHasApproxSize(analysis, "VarPointsTo", 2755187)
		relationHasApproxSize(analysis, "ReachableContext", 9297)
		noSanityErrors(analysis)
	}
}
