package org.clyze.doop

import org.clyze.analysis.Analysis
import org.clyze.doop.common.Parameters
import spock.lang.Unroll

class FactsSubsetTest extends ServerAnalysisTests {

    // @spock.lang.Ignore
    @Unroll
    def "Server analysis test 006 (hello world) / facts-subset-APP"() {
        when:
        Analysis analysis = analyzeTest("006-hello-world", ["--Xfacts-subset", "APP", "--platform", "java_8"])

        then:
        true

        where:
        subset << Parameters.FactsSubSet.valueSet()
    }

}
