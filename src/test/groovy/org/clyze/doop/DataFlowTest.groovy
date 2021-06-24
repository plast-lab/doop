package org.clyze.doop

import org.clyze.analysis.Analysis
import spock.lang.Unroll

/** Test the 'data-flow' analysis. */
class DataFlowTest extends DoopSpec {

    // @spock.lang.Ignore
    @Unroll
    def "Data-flow analysis test"() {
        when:
        Main.main(['-i', Artifacts.HELLO_JAR,
                   '--id', 'data-flow-test', '--cache',
                   '--information-flow', 'minimal',
                   '-a', 'data-flow'] as String[])
        Analysis analysis = Main.analysis

        then:
	    TestUtils.fileExists(analysis, 'database/Flows.csv')
        TestUtils.fileExists(analysis, 'database/PointerFlows.csv')
        TestUtils.fileExists(analysis, 'database/SelfFlows.csv')
    }
}
