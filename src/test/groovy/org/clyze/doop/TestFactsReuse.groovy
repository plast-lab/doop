package org.clyze.doop

import org.clyze.analysis.Analysis
import org.clyze.doop.core.Doop
import spock.lang.Unroll

class TestFactsReuse extends DoopSpec {

    String getHelloFacts() { "${Doop.doopHome}/test-hello-facts" }

    // @spock.lang.Ignore
    @Unroll
    def "Facts reuse + CHA"() {
        when:
        // Generate facts and stop.
        Main.main((String[])
                  ['-i', Artifacts.HELLO_JAR,
                   '--id', 'hello-facts-run',
                   '--Xstop-at-facts', helloFacts,
                   '--server-cha', '--generate-jimple'])
        Analysis analysis1 = Main.analysis
        // Reuse facts from previous step.
        Main.main((String[])
                  ['--Xextend-facts', helloFacts,
                   '-a', 'types-only',
                   '--regex', 'Main',
                   '--main', 'DUMMY',
                   '--skip-code-factgen',
                   '--server-logic',
                   '--stats', 'none',
                   '--id', 'hello-facts-analyzed'])
        Analysis analysis2 = Main.analysis

        then:
        // We just check that the run succeeds.
        true == true
    }

    def cleanup() {
        println "Deleting directory: ${helloFacts}"
        (new File(helloFacts)).delete()
    }
}
