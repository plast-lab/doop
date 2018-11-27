package org.clyze.doop

import org.clyze.analysis.Analysis
import spock.lang.Specification
import spock.lang.Unroll
import static org.clyze.doop.TestUtils.*

/**
 * Test micro analysis.
 */
class CrudeScalerTest extends Specification {

	Analysis analysis

	@Unroll
	def "Crude testing micro analysis"() {
		when:
		Main.main((String[])["-i", "http://centauri.di.uoa.gr:8081/artifactory/DaCapo-benchmarks/2006/antlr.jar", "-a", "fully-guided-context-sensitive", "--id", "antlr-scaler", "--dacapo", "--scaler-pre", "--fact-gen-cores", "1", "--platform", "java_8"])
		analysis = Main.analysis

		then:
		relationHasApproxSize(analysis, "CallGraphEdge", 1086574)
		relationHasApproxSize(analysis, "VarPointsTo", 6643270)
                relationHasApproxSize(analysis, "ApplicationMethod", 2680)
                relationHasApproxSize(analysis, "Reachable", 10311)
                relationHasApproxSize(analysis, "basic-ResolveInvocation", 5290606)
	}
}
