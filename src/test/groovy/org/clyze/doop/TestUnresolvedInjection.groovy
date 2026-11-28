package org.clyze.doop

import org.clyze.analysis.Analysis
import spock.lang.Specification

/**
 * Test 134 (--open-programs jackee): @Autowired fields the bean resolver cannot
 * resolve are still filled, so the calls on them in PayController.pay resolve. PayController.gateway has two candidate beans and a
 * name matching neither, so it gets both; PayController.clock has no candidate
 * bean, so it gets a mock of its declared type. Both fields are also written by
 * the constructor, so neither is an environment-only field.
 */
class TestUnresolvedInjection extends DoopSpec {

    def "Test 134 (unresolved injection points are filled)"() {
        when:
        String dir = 'tests/134-unresolved-injection'
        Analysis analysis = analyzeTest('134-unresolved-injection', "${dir}/134-unresolved-injection.jar",
                                        ['-l', "${dir}/134-unresolved-injection-lib.jar", '--open-programs', 'jackee',
                                         '--platform', 'java_8'] + souffleInterpreter, 'context-insensitive', 'test-134-unresolved-injection')
        String pay = '<app.PayController: java.lang.String pay()>'
        List<String> targets = new File("${analysis.database}/CallGraphEdge.csv").readLines()
                .collect { it.split('\t') }.findAll { it[1].startsWith(pay) }.collect { it[3] }

        then:
        targets.contains('<app.StripeGateway: java.lang.String pay()>')
        targets.contains('<app.PaypalGateway: java.lang.String pay()>')
        targets.contains('<app.SystemClock: long now()>')
        unresolved(analysis).contains('<app.PayController: app.Gateway gateway>\tfield\tambiguous')
        unresolved(analysis).contains('<app.PayController: app.Clock clock>\tfield\tno-candidate')
    }

    static List<String> unresolved(Analysis analysis) {
        new File("${analysis.database}/UnresolvedInjection.csv").readLines()
    }
}
