package org.clyze.doop

import org.clyze.analysis.Analysis
import spock.lang.Specification

/**
 * Test 120: an instance lambda capturing `this` and a value. Its invokedynamic
 * arguments are (this, payload); with them reversed, the lambda body ran on the
 * payload and Box.value never received it.
 */
class TestInvokedynamicArgOrder extends DoopSpec {

    def "Test 120 (invokedynamic arguments in bytecode order)"() {
        when:
        Analysis analysis = analyzeTest('120-indy-arg-order', 'tests/120-indy-arg-order/120-indy-arg-order.jar',
                                        ['--platform', 'java_8'] + souffleInterpreter, 'context-insensitive', 'test-120-indy-arg-order')

        then:
        new File("${analysis.database}/InstanceFieldPointsTo.csv").readLines().collect { it.split('\t') }.any {
            it[1] == '<Main: void main(java.lang.String[])>/new Main$Payload/0' && it[2] == '<Main$Box: java.lang.Object value>'
        }
    }
}
