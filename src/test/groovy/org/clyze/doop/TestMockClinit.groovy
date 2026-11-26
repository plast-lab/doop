package org.clyze.doop

import org.clyze.analysis.Analysis
import spock.lang.Specification

/**
 * Test 130 (--open-programs jackee): a mocked bean is an instance the container
 * created, so its class was initialized: its static initializer is reachable,
 * and so is what that initializer calls.
 */
class TestMockClinit extends DoopSpec {

    def "Test 130 (a mock initializes its class)"() {
        when:
        String dir = 'tests/130-mock-clinit'
        Analysis analysis = analyzeTest('130-mock-clinit', "${dir}/130-mock-clinit.jar",
                                        ['-l', "${dir}/130-mock-clinit-lib.jar", '--open-programs', 'jackee',
                                         '--platform', 'java_8'] + souffleInterpreter, 'context-insensitive', 'test-130-mock-clinit')
        List<String> reachable = new File("${analysis.database}/Reachable.csv").readLines()

        then:
        reachable.contains('<app.GreetingService: void <clinit>()>')
        reachable.contains('<app.Registry: void register(java.lang.String)>')
    }
}
