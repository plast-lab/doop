package org.clyze.doop

import org.clyze.analysis.Analysis
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Test micro analysis.
 */
class CrudeMicroTest extends Specification {

	Analysis analysis

	@Unroll
	def "Crude testing micro analysis"() {
		when:
		Main.main((String[])["-i", "http://centauri.di.uoa.gr:8081/artifactory/DaCapo-benchmarks/2006/antlr.jar", "-a", "micro", "--id", "antlr-micro", "--dacapo", "--Xstats-none"])
		analysis = Main.analysis

		then:
		relationHasApproxSize("ApplicationMethod", 2680)
		relationHasApproxSize("ArrayIndexPointsTo", 7497)
		relationHasApproxSize("ArrayIndexPointsTo", 7497)
		relationHasApproxSize("Assign", 32658)
		relationHasApproxSize("basic-ResolveInvocation", 3992396)
		relationHasApproxSize("CallGraphEdge", 13873)
		relationHasApproxSize("InstanceFieldPointsTo", 539551)
		relationHasApproxSize("StaticFieldPointsTo", 783)
		relationHasApproxSize("VarPointsTo", 624730)
	}

	void relationHasApproxSize(String relation, int expectedSize) {
		int actualSize = new File("${analysis.database}/${relation}.csv").readLines().size()
		// We expect numbers to deviate by 10%.
		assert actualSize > (expectedSize * 0.9)
		assert actualSize < (expectedSize * 1.1)
	}
}
