package org.clyze.doop

import org.clyze.analysis.Analysis
import spock.lang.Specification

/**
 * Test 124: a multi-release jar is read as the analysed platform's JVM loads it:
 * on java_17, META-INF/versions/11/Versioned.class replaces the base entry.
 */
class TestMultiReleaseJar extends DoopSpec {

    def "Test 124 (multi-release jar on java_17)"() {
        when:
        Analysis analysis = analyzeTest('124-multi-release', 'tests/124-multi-release/124-multi-release.jar',
                                        ['--platform', 'java_17'] + souffleInterpreter, 'context-insensitive', 'test-124-multi-release')

        then:
        new File("${analysis.database}/Reachable.csv").readLines().any { it.startsWith('<Versioned: java.lang.String onlyIn11()>') }
    }
}
