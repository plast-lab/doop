package org.clyze.doop

import org.clyze.analysis.Analysis
import spock.lang.Specification
import spock.lang.Unroll
import static org.clyze.doop.TestUtils.*

/**
 * Test Souffle analysis mode.
 */
class CrudeSouffleTest extends Specification {

	// @spock.lang.Ignore
	@Unroll
	def "Crude testing Souffle mode (based on sample metrics similarity) using [#scenario]"() {
		when:
		def propertyFile = this.class.getResource("/scenarios/$scenario").file
		Main.main2((String[])["--stats", "full", "--platform", "java_7", "-p", propertyFile])
		Analysis analysis = Main.analysis

		then:
		metricIsApprox(analysis, "var points-to (SENS)", vpt)
		metricIsApprox(analysis, "instance field points-to (INS)", fpt)
		metricIsApprox(analysis, "call graph edges (INS)", cge)
		metricIsApprox(analysis, "polymorphic virtual call sites", pCalls)
		metricIsApprox(analysis, "reachable casts that may fail", fCasts)
		metricIsApprox(analysis, "app reachable casts", aRCasts)
		metricIsApprox(analysis, "reachable variables (INS)", rVarsINS)
		metricIsApprox(analysis, "reachable variables (SENS)", rVarsSENS)
		metricIsApprox(analysis, "reachable methods (INS)", rMethodsINS)
		metricIsApprox(analysis, "reachable methods (SENS)", rMethodsSENS)
		metricIsApprox(analysis, "app virtual call sites (statically)", aVCS)
		metricIsApprox(analysis, "app reachable virtual call sites", aRVCS)
		metricIsApprox(analysis, "non-reachable app concrete methods", nRACM)

		where:
		scenario                                  | vpt      | fpt     | cge    | pCalls | fCasts | aRCasts | rVarsINS | rVarsSENS | rMethodsINS | rMethodsSENS | aVCS    | aRVCS | nRACM
		"antlr-insensitive-tamiflex.properties"   | 2860106  | 239868  | 57628  | 2010   | 1153   | 322     | 98038    | 98038     | 8431        | 8431         | 19594   | 16826 | 964
		"antlr-1call-tamiflex.properties"         | 9292112  | 192361  | 55859  | 1887   | 954    | 322     | 89115    | 373940    | 8316        | 56017        | 19625   | 16855 | 964
		"antlr-1objH-tamiflex.properties"         | 5252781  | 82954   | 54545  | 1811   | 926    | 322     | 88348    | 371868    | 8224        | 56017        | 19625   | 16855 | 965
		"antlr-insensitive-reflection.properties" | 8376198  | 1111250 | 56606  | 1814   | 1456   | 15      | 97936    | 97936     | 10724       | 10724        | 19625   | 480   | 2328
	}
}
