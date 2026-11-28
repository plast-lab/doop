package org.clyze.doop

import org.clyze.analysis.Analysis
import spock.lang.Specification

/**
 * Test 121: MethodHandle.invokeExact is signature-polymorphic; MethodHandle.type()
 * is an ordinary method and must not be written as a polymorphic invocation.
 */
class TestPolymorphicSignature extends DoopSpec {

    def "Test 121 (only native varargs MethodHandle methods are signature-polymorphic)"() {
        when:
        Analysis analysis = analyzeTest('121-polymorphic-signature', 'tests/121-polymorphic-signature/121-polymorphic-signature.jar',
                                        ['--platform', 'java_8', '--facts-only'], 'context-insensitive', 'test-121-polymorphic-signature')
        List<String> mainPolymorphic = new File("${analysis.database}/PolymorphicInvocation.facts").readLines()
            .findAll { it.startsWith('<Main: void main(java.lang.String[])>/') }.collect { it.split('\t')[1] }

        then:
        mainPolymorphic == ['invokeExact']
    }
}
