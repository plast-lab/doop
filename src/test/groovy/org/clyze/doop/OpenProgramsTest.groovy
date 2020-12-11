package org.clyze.doop

import org.clyze.analysis.Analysis
import spock.lang.Specification
import spock.lang.Unroll
import static org.clyze.doop.TestUtils.*

/**
 * Test open-program analysis.
 */
class OpenProgramsTest extends DoopSpec {

	// @spock.lang.Ignore
	@Unroll
	def "Testing support for open programs"() {
		when:
		List<String> options = ['--open-programs', 'concrete-types', '--stats', 'full', '--platform', 'java_7', '--no-standard-exports'] + testExports
		Analysis analysis = analyzeTest('ivy-open-programs', 'org.apache.ivy:ivy:2.3.0', options, 'context-insensitive')

		then:
		metricIsApprox(analysis, "call graph edges (INS)", 449_742)
		metricIsApprox(analysis, "reachable methods (INS)", 67_412)
		metricIsApprox(analysis, "array index points-to (INS)", 531_103)
		metricIsApprox(analysis, "instance field points-to (INS)", 6_055_773)
		metricIsApprox(analysis, "var points-to (INS)", 69_132_048)
	}
}
