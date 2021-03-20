package org.clyze.doop

import org.clyze.analysis.Analysis
import org.clyze.doop.core.Doop
import spock.lang.Unroll

class TestFactsReuse extends DoopSpec {

    String getHelloFacts() { "test-hello-facts" }

    // @spock.lang.Ignore
    @Unroll
    def "Facts reuse + CHA"() {
        when:
        // Generate facts and stop.
        Main.main((String[])
                  ['-i', Artifacts.HELLO_JAR,
                   '--id', helloFacts,
                   '--facts-only',
                   '--server-cha', '--generate-jimple'])
        Analysis analysis1 = Main.analysis
        // Reuse facts from previous step.
        Main.main((String[])
                  ['--input-id', helloFacts,
                   '-a', 'types-only',
                   '--regex', 'Main',
                   '--main', 'DUMMY',
                   '--server-logic',
                   '--stats', 'none',
                   '--id', 'hello-facts-analyzed'])
        Analysis analysis2 = Main.analysis

        then:
        // We just check that the run succeeds.
        true == true
    }

    def cleanup() {
        def dir = new File("${Doop.doopOut}/$helloFacts")
        println "Deleting directory: $dir"
        dir.delete()
        dir = new File("${Doop.doopOut}/hello-facts-analyzed")
        println "Deleting directory: $dir"
        dir.delete()
    }
}
