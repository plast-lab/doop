package org.clyze.doop

import org.clyze.analysis.Analysis
import org.clyze.doop.common.Parameters
import spock.lang.Unroll

class TestFactsSubset extends ServerAnalysisTests {

    // @spock.lang.Ignore
    @Unroll
    def "Server analysis test 006 (hello world) / facts-subset"(String subset) {
        when:
        Analysis analysis = analyzeTest('006-hello-world', ['--Xfacts-subset', subset, '--platform', 'java_8', '--facts-only'])

        then:
        true

        where:
        subset << Parameters.FactsSubSet.valueSet()
    }

}
