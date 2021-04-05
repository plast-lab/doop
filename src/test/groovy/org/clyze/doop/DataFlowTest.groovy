package org.clyze.doop

import org.clyze.analysis.Analysis
import spock.lang.Unroll

/** Test the 'data-flow' analysis. */
class DataFlowTest extends DoopSpec {

    // @spock.lang.Ignore
    @Unroll
    def "Data-flow analysis test"() {
        when:
        Main.main((String[])
                  ['-i', Artifacts.HELLO_JAR,
                   '--id', 'data-flow-test', '--cache',
                   '-a', 'data-flow'])
        Analysis analysis = Main.analysis

        then:
	    TestUtils.fileExists(analysis, 'database/Flows.csv')
        TestUtils.fileExists(analysis, 'database/PointerFlows.csv')
        TestUtils.fileExists(analysis, 'database/SelfFlows.csv')
    }
}
