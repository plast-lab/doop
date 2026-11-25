package org.clyze.doop

import org.clyze.analysis.Analysis
import spock.lang.Specification

/**
 * Test 126: with --deepspatch, a provider listed in META-INF/services is
 * constructed with its no-arg constructor, as ServiceLoader does.
 */
class TestDeepspatchServiceLoader extends DoopSpec {

    def "Test 126 (--deepspatch constructs ServiceLoader providers)"() {
        when:
        Analysis analysis = analyzeTest('126-deepspatch-service-loader', 'tests/126-deepspatch-service-loader/126-deepspatch-service-loader.jar',
                                        ['--platform', 'java_8', '--deepspatch'] + souffleInterpreter, 'context-insensitive', 'test-126-deepspatch-service-loader')
        Map<String, String> reason = new File("${analysis.database}/MockObjectReason.csv").readLines()
            .collect { it.split('\t') }.collectEntries { [(it[0]): it[1]] }

        then:
        new File("${analysis.database}/Reachable.csv").readLines().any { it.startsWith('<EnglishGreeter: void <init>()>') }
        reason['EnglishGreeter::MockObject'] == 'service-provider'
    }
}
