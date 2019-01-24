package org.clyze.doop

import org.clyze.analysis.Analysis
import spock.lang.Specification
import spock.lang.Unroll
import static org.clyze.doop.TestUtils.*

/**
 * Test open-program analysis.
 */
class OpenProgramsTest extends Specification {

	@Unroll
	def "Testing support for open programs"() {
		when:
		Main.main((String[])["-i", "org.apache.ivy:ivy:2.3.0", "-a", "context-insensitive", "--id", "ivy-open-programs", "--open-programs", "concrete-types", "--Xstats-full", "--platform", "java_7"])
		Analysis analysis = Main.analysis

		then:
		metricIsApprox(analysis, "call graph edges (INS)", 371_681)
		metricIsApprox(analysis, "reachable methods (INS)", 60_496)
		metricIsApprox(analysis, "array index points-to (INS)", 467_360)
		metricIsApprox(analysis, "instance field points-to (INS)", 5_384_015)
		metricIsApprox(analysis, "var points-to (INS)", 64_934_743)
	}
}
