package org.clyze.doop

import org.clyze.analysis.Analysis
import org.clyze.doop.core.Doop
import org.clyze.doop.soot.DoopConventions
import org.clyze.jimple.Main
import spock.lang.Unroll

class TestJimpleParser extends DoopSpec {
    // @spock.lang.Ignore
    @Unroll
    def "Jimple parser test"() {
        when:
        String id = "006-hello-world-jimple"
        String factsDir = "${Doop.doopHome}/${id}-facts"
        Analysis analysis = analyzeTest(id, Artifacts.HELLO_JAR,
                                        ["--generate-jimple", "--Xstop-at-facts", factsDir])
        String jimpleDir = DoopConventions.jimpleDir(factsDir)
        println "Running Jimple parser (in ${jimpleDir})..."
        Main.main([jimpleDir] as String[])
        println "Jimple parser done."

        then:
        assert true
    }
}
