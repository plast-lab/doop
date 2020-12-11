package org.clyze.doop

import org.clyze.analysis.Analysis
import spock.lang.Specification
import spock.lang.Unroll
import static org.clyze.doop.TestUtils.*

/**
 * Test micro analysis.
 */
class CrudeMicroTest extends Specification {

	// @spock.lang.Ignore
	@Unroll
	def "Crude testing micro analysis"() {
		when:
		Main.main((String[])["-i", Artifacts.ANTLR_JAR, "-a", "micro", "--id", "antlr-micro", "--dacapo", "--stats", "none", "--fact-gen-cores", "1", "--platform", "java_7"])
		Analysis analysis = Main.analysis

		then:
		relationHasApproxSize(analysis, "ApplicationMethod", 2680)
		relationHasApproxSize(analysis, "ArrayIndexPointsTo", 7497)
		relationHasApproxSize(analysis, "Assign", 32658)
		relationHasApproxSize(analysis, "CallGraphEdge", 13873)
		relationHasApproxSize(analysis, "InstanceFieldPointsTo", 539551)
		relationHasApproxSize(analysis, "StaticFieldPointsTo", 783)
		relationHasApproxSize(analysis, "VarPointsTo", 624730)
	}
}
