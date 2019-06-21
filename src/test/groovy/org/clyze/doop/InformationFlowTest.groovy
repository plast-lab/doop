package org.clyze.doop

import org.clyze.analysis.Analysis
import org.clyze.doop.core.Doop
import spock.lang.Unroll
import static org.clyze.doop.TestUtils.*

/**
 * Test information flow (P/Taint).
 */
class InformationFlowTest extends DoopSpec {

	// @spock.lang.Ignore
	@Unroll
	def "Information flow (P/Taint) test"() {
		when:
		List args = ["-i", Artifacts.ANDROIDTERM_APK,
					 "-a", "context-insensitive",
					 "--platform", "android_25_fulljars",
                     "--information-flow", "android",
					 "--id", "test-android-androidterm-information-flow",
					 "--Xextra-logic", "${Doop.souffleAddonsPath}/testing/test-exports.dl",
					 "--generate-jimple", "--Xstats-full", "-Ldebug"]
		Main.main((String[])args)
		Analysis analysis = Main.analysis

        then:
        relationHasApproxSize(analysis, "AppTaintedVar", 2663)
        relationHasApproxSize(analysis, "Stats_Simple_Application_TaintedVarPointsTo", 55194)
    }
}
