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
		metricIsApprox(analysis, "call graph edges (INS)", 312_457)
		metricIsApprox(analysis, "reachable methods (INS)", 53_874)
		metricIsApprox(analysis, "array index points-to (INS)", 396_462)
		metricIsApprox(analysis, "instance field points-to (INS)", 4_469_577)
		metricIsApprox(analysis, "var points-to (INS)", 51_629_303)
	}
}
