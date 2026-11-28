package org.clyze.doop

import org.clyze.analysis.Analysis
import spock.lang.Specification

/**
 * Test 125: the environment supplies main's String[] argument and its elements;
 * they are mocked, and every mock object has a reason (MockObjectReason).
 */
class TestMockProvenance extends DoopSpec {

    def "Test 125 (every mock object has a provenance reason)"() {
        when:
        Analysis analysis = analyzeTest('125-mock-provenance', 'tests/125-mock-provenance/125-mock-provenance.jar',
                                        ['--platform', 'java_8'] + souffleInterpreter, 'context-insensitive', 'test-125-mock-provenance')
        Set<String> mocks = new File("${analysis.database}/MockObject.csv").readLines().collect { it.split('\t')[0] } as Set
        Map<String, String> reason = new File("${analysis.database}/MockObjectReason.csv").readLines()
            .collect { it.split('\t') }.collectEntries { [(it[0]): it[1]] }

        then:
        !mocks.isEmpty()
        mocks.findAll { !reason.containsKey(it) }.isEmpty()
        reason['java.lang.String[]::MockObject'] == 'entry-param'
    }
}
