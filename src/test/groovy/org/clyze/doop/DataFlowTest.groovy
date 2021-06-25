package org.clyze.doop

import org.clyze.analysis.Analysis
import spock.lang.Unroll

/** Test the 'data-flow' analysis. */
class DataFlowTest extends DoopSpec {

    // @spock.lang.Ignore
    @Unroll
    def "Data-flow/information-flow analysis test"() {
        when:
        Main.main(['-i', Artifacts.DROIDBENCH_CLONE1,
                   '--id', 'data-flow-test',
                   '--platform', 'android_25_fulljars',
                   '--information-flow', 'android',
                   '--souffle-mode', 'interpreted',
                   '--stats', 'none',
                   '-a', 'data-flow'] as String[])
        Analysis analysis = Main.analysis

        then:
	    TestUtils.fileExists(analysis, 'database/Flows.csv')
        TestUtils.fileExists(analysis, 'database/PointerFlows.csv')
        TestUtils.fileExists(analysis, 'database/SelfFlows.csv')
        TestUtils.relationHasExactSize(analysis, 'LeakingTaintedInformation', 1)
    }
}
