package org.clyze.doop

import org.clyze.analysis.Analysis
import spock.lang.Specification
import spock.lang.Unroll
import static org.clyze.doop.TestUtils.*

/**
 * Test open-program analysis.
 */
class OpenProgramsTest extends Specification {

	Analysis analysis

	@Unroll
	def "Testing support for open programs"() {
		when:
		Main.main((String[])["-i", "org.apache.ivy:ivy:2.3.0", "-a", "context-insensitive", "--id", "ivy-open-programs", "--open-programs", "concrete-types", "--Xstats-full"])
		analysis = Main.analysis

		then:
		relationHasApproxSize(analysis, "CallGraphEdge", 245415)
		relationHasApproxSize(analysis, "Reachable", 43092)
		relationHasApproxSize(analysis, "Stats_Simple_Application_ArrayIndexPointsTo", 5692)
		relationHasApproxSize(analysis, "Stats_Simple_Application_InstanceFieldPointsTo", 60468)
		relationHasApproxSize(analysis, "VarPointsTo", 32832599)
	}
}
