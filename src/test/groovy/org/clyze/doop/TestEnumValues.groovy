package org.clyze.doop

import org.clyze.analysis.Analysis
import spock.lang.Specification

/**
 * Test 128: Enum.valueOf finds the constants through Class.getEnumConstantsShared,
 * which calls the enum's values() reflectively; values() of the enum whose class
 * object reaches it is reachable, the other enum's is not.
 */
class TestEnumValues extends DoopSpec {

    def "Test 128 (getEnumConstantsShared calls the enum's values())"() {
        when:
        Analysis analysis = analyzeTest('128-enum-values', 'tests/128-enum-values/128-enum-values.jar',
                                        ['--platform', 'java_8'] + souffleInterpreter, 'context-insensitive', 'test-128-enum-values')
        List<String> reachable = new File("${analysis.database}/Reachable.csv").readLines()

        then:
        reachable.contains('<Main$Color: Main$Color[] values()>')
        !reachable.contains('<Main$Shade: Main$Shade[] values()>')
    }
}
