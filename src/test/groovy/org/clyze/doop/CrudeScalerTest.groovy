package org.clyze.doop

import org.clyze.analysis.Analysis
import spock.lang.Specification
import spock.lang.Unroll
import static org.clyze.doop.TestUtils.*

/**
 * Test micro analysis.
 */
class CrudeScalerTest extends Specification {

	// @spock.lang.Ignore
	@Unroll
	def "Crude testing scaler analysis"() {
		when:
		Main.main((String[])["-i", Artifacts.ANTLR_JAR, "-a", "fully-guided-context-sensitive", "--id", "antlr-scaler", "--dacapo", "--Xscaler-pre", "--fact-gen-cores", "1", "--platform", "java_8"])
		Analysis analysis = Main.analysis

		then:
		relationHasApproxSize(analysis, "CallGraphEdge", 460285)
		relationHasApproxSize(analysis, "VarPointsTo", 4171899)
		relationHasApproxSize(analysis, "Reachable", 10297)
	}
}
