package org.clyze.doop

import org.clyze.analysis.Analysis
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Test 123: a .war and a Spring Boot jar are analysed as their class loader sees
 * them: WEB-INF/classes and BOOT-INF/classes are application code, nested jars
 * (WEB-INF/lib, BOOT-INF/lib) are libraries.
 */
class TestDeployables extends DoopSpec {

    @Unroll
    def "Test 123 (deployables: #input)"() {
        when:
        Analysis analysis = analyzeTest('123-deployables', "tests/123-deployables/${input}",
                                        ['--platform', 'java_8', '--facts-only'], 'context-insensitive', "test-123-${input}")
        Set<String> app = new File("${analysis.database}/ApplicationClass.facts").readLines() as Set
        Map<String, String> artifact = new File("${analysis.database}/Class-Artifact.facts").readLines()
            .collect { it.split('\t') }.collectEntries { [(it[1]): it[0]] }

        then:
        app.contains('app.Hello')
        !app.contains('lib.Util')
        artifact['lib.Util'] == 'util.jar'

        where:
        input << ['123-deployables.war', '123-deployables-boot.jar']
    }
}
